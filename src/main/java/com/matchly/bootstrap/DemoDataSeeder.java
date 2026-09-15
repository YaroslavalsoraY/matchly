package com.matchly.bootstrap;

import com.matchly.common.util.AvatarGenerator;
import com.matchly.config.MatchlyProperties;
import com.matchly.interest.Interest;
import com.matchly.interest.InterestRepository;
import com.matchly.profile.Gender;
import com.matchly.profile.Preference;
import com.matchly.profile.Profile;
import com.matchly.profile.ProfileDetails;
import com.matchly.profile.ProfilePhoto;
import com.matchly.profile.ProfilePhotoRepository;
import com.matchly.profile.ProfileRepository;
import com.matchly.reaction.ReactionService;
import com.matchly.reaction.ReactionType;
import com.matchly.reaction.dto.ReactionRequest;
import com.matchly.reaction.dto.ReactionResponse;
import com.matchly.user.User;
import com.matchly.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Наполняет базу демонстрационными анкетами, аватарами и реакциями при первом запуске,
 * чтобы лента и алгоритмы рекомендаций работали сразу. Генерация детерминирована (фиксированный seed),
 * повторный запуск ничего не добавляет. Пароль всех демо-аккаунтов: {@value #PASSWORD}.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    public static final String EMAIL_PATTERN = "demo%d@matchly.local";
    public static final String PASSWORD = "demo1234";

    private static final List<String> FEMALE_NAMES = List.of("Анна", "Мария", "Екатерина", "Ольга", "Дарья", "Полина",
            "Алина", "Ксения", "Виктория", "Елена", "Наталья", "София", "Ирина", "Юлия", "Кристина", "Анастасия",
            "Марина", "Вероника", "Алиса", "Татьяна");
    private static final List<String> MALE_NAMES = List.of("Иван", "Дмитрий", "Алексей", "Максим", "Артём", "Никита",
            "Сергей", "Андрей", "Михаил", "Кирилл", "Егор", "Павел", "Роман", "Владимир", "Илья", "Денис", "Тимур",
            "Олег", "Глеб", "Антон");
    private static final List<String> CITIES = List.of("Москва", "Москва", "Москва", "Санкт-Петербург", "Санкт-Петербург",
            "Казань", "Новосибирск", "Екатеринбург", "Нижний Новгород");
    private static final List<String> ACTIVITIES = List.of("гулять по городу", "смотреть закаты", "пробовать новые кафе",
            "ездить в путешествия", "болтать до утра", "ходить в походы", "смотреть кино под пледом", "готовить вместе");
    /** Максимум реакций на анкету; при этом не более половины подходящих кандидатов, чтобы лента не пустела. */
    private static final int MAX_REACTIONS_PER_PROFILE = 6;
    private static final double LIKE_PROBABILITY = 0.6;

    private final MatchlyProperties properties;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ProfilePhotoRepository photoRepository;
    private final InterestRepository interestRepository;
    private final ReactionService reactionService;
    private final PasswordEncoder passwordEncoder;
    private final AvatarGenerator avatarGenerator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        MatchlyProperties.Seed seed = properties.seed();
        if (!seed.enabled() || seed.profiles() == 0) {
            log.info("Demo data seeding is disabled");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(EMAIL_PATTERN.formatted(1))) {
            log.info("Demo data already present, seeding skipped");
            return;
        }
        List<Interest> interests = interestRepository.findAll();
        if (interests.isEmpty()) {
            log.warn("No interests in the dictionary, demo data seeding skipped");
            return;
        }

        Random random = new Random(42);
        LocalDate today = LocalDate.now();
        String passwordHash = passwordEncoder.encode(PASSWORD);

        List<Profile> profiles = new ArrayList<>();
        for (int i = 1; i <= seed.profiles(); i++) {
            profiles.add(createProfile(i, passwordHash, interests, random, today));
        }
        int[] stats = createReactions(profiles, random, today);

        log.info("Demo data seeded: {} profiles, {} likes, {} skips, {} matches (login: demo1@matchly.local / {})",
                profiles.size(), stats[0], stats[1], stats[2], PASSWORD);
    }

    private Profile createProfile(int index, String passwordHash, List<Interest> allInterests, Random random, LocalDate today) {
        Gender gender = index % 2 == 0 ? Gender.MALE : Gender.FEMALE;
        List<String> names = gender == Gender.MALE ? MALE_NAMES : FEMALE_NAMES;
        String name = names.get((index / 2) % names.size());

        int age = 20 + random.nextInt(26);
        LocalDate birthDate = today.minusYears(age).minusDays(random.nextInt(300));
        Preference lookingFor = random.nextDouble() < 0.15 ? Preference.EVERYONE
                : gender == Gender.MALE ? Preference.FEMALE : Preference.MALE;
        int ageMin = Math.max(Profile.MIN_AGE, age - 5 - random.nextInt(10));
        int ageMax = Math.min(Profile.MAX_AGE, age + 5 + random.nextInt(10));

        Set<Interest> picked = pickInterests(allInterests, 3 + random.nextInt(4), random);
        String bio = buildBio(picked, random);

        User user = userRepository.save(User.register(EMAIL_PATTERN.formatted(index), passwordHash));
        Profile profile = new Profile(user);
        profile.update(new ProfileDetails(name, birthDate, gender, lookingFor, ageMin, ageMax,
                CITIES.get(random.nextInt(CITIES.size())), bio, "@demo" + index, true), picked, today);
        profile = profileRepository.save(profile);

        byte[] avatar = avatarGenerator.generate(name, index);
        photoRepository.save(new ProfilePhoto(profile, AvatarGenerator.CONTENT_TYPE, avatar));
        return profile;
    }

    /** Каждая анкета реагирует на несколько подходящих ей анкет; взаимные лайки становятся матчами. */
    private int[] createReactions(List<Profile> profiles, Random random, LocalDate today) {
        int likes = 0;
        int skips = 0;
        int matches = 0;
        for (Profile source : profiles) {
            List<Profile> compatible = profiles.stream()
                    .filter(candidate -> !candidate.equals(source) && source.isMutuallyCompatibleWith(candidate, today))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(compatible, random);
            int reactions = Math.min(MAX_REACTIONS_PER_PROFILE, compatible.size() / 2);
            for (Profile target : compatible.subList(0, reactions)) {
                ReactionType type = random.nextDouble() < LIKE_PROBABILITY ? ReactionType.LIKE : ReactionType.SKIP;
                ReactionResponse response = reactionService.react(source.getUser().getId(),
                        new ReactionRequest(target.getId(), type));
                if (type == ReactionType.LIKE) {
                    likes++;
                } else {
                    skips++;
                }
                if (response.matched()) {
                    matches++;
                }
            }
        }
        return new int[]{likes, skips, matches};
    }

    private static Set<Interest> pickInterests(List<Interest> all, int count, Random random) {
        List<Interest> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, random);
        return new HashSet<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    private static String buildBio(Set<Interest> interests, Random random) {
        List<String> names = interests.stream().map(i -> i.getName().toLowerCase(Locale.ROOT)).sorted().toList();
        String first = names.get(0);
        String second = names.size() > 1 ? names.get(1) : "хорошую компанию";
        return "Люблю %s и %s. Ищу того, с кем можно %s.".formatted(first, second,
                ACTIVITIES.get(random.nextInt(ACTIVITIES.size())));
    }
}
