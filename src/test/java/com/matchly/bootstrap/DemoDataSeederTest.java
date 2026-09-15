package com.matchly.bootstrap;

import com.matchly.matching.MatchRepository;
import com.matchly.profile.ProfilePhotoRepository;
import com.matchly.profile.ProfileRepository;
import com.matchly.reaction.ReactionRepository;
import com.matchly.user.User;
import com.matchly.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Генератор демо-данных проверяется в отдельном контексте с собственной in-memory базой,
 * чтобы демо-анкеты не смешивались с данными остальных тестов.
 */
@SpringBootTest(properties = {
        "matchly.seed.enabled=true",
        "matchly.seed.profiles=12",
        "spring.datasource.url=jdbc:h2:mem:seedtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder seeder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private ProfilePhotoRepository photoRepository;
    @Autowired
    private ReactionRepository reactionRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedsProfilesPhotosReactions_andIsIdempotent() {
        assertThat(userRepository.count()).isEqualTo(13);          // администратор + 12 демо-аккаунтов
        assertThat(profileRepository.count()).isEqualTo(12);
        assertThat(photoRepository.count()).isEqualTo(12);
        assertThat(reactionRepository.count()).isGreaterThan(0);
        assertThat(matchRepository.count()).isGreaterThanOrEqualTo(0);

        User demo = userRepository.findByEmailIgnoreCase("demo1@matchly.local").orElseThrow();
        assertThat(passwordEncoder.matches(DemoDataSeeder.PASSWORD, demo.getPasswordHash())).isTrue();
        assertThat(profileRepository.findByUserId(demo.getId())).isPresent();

        long reactionsBefore = reactionRepository.count();
        seeder.run(null);

        assertThat(userRepository.count()).isEqualTo(13);
        assertThat(reactionRepository.count()).isEqualTo(reactionsBefore);
    }
}
