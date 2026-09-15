package com.matchly.profile;

import com.matchly.common.entity.BaseEntity;
import com.matchly.common.exception.BusinessRuleException;
import com.matchly.interest.Interest;
import com.matchly.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Анкета пользователя: то, что видят другие, и настройки поиска (кого и какого возраста показывать).
 * Инварианты (совершеннолетие, корректный диапазон возраста) проверяются внутри сущности,
 * поэтому её невозможно привести в недопустимое состояние снаружи.
 */
@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseEntity {

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 99;
    public static final int MAX_INTERESTS = 10;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "looking_for", nullable = false, length = 10)
    private Preference lookingFor;

    @Column(name = "age_min", nullable = false)
    private int ageMin;

    @Column(name = "age_max", nullable = false)
    private int ageMax;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 1000)
    private String bio;

    /** Контакт (например, ник в Telegram). Раскрывается только участникам взаимного матча. */
    @Column(nullable = false, length = 100)
    private String contact;

    /** Скрытая анкета не участвует в рекомендациях, но остаётся доступной владельцу. */
    @Column(nullable = false)
    private boolean visible = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "profile_interests",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "interest_id"))
    private Set<Interest> interests = new HashSet<>();

    public Profile(User user) {
        this.user = user;
    }

    /**
     * Заполняет анкету. Используется и при создании, и при редактировании.
     *
     * @param today текущая дата (передаётся явно, чтобы правила были тестируемы)
     */
    public void update(ProfileDetails details, Set<Interest> newInterests, LocalDate today) {
        int age = Period.between(details.birthDate(), today).getYears();
        if (age < MIN_AGE) {
            throw new BusinessRuleException("You must be at least " + MIN_AGE + " years old");
        }
        if (details.ageMin() > details.ageMax()) {
            throw new BusinessRuleException("Minimum age must not exceed maximum age");
        }
        if (details.ageMin() < MIN_AGE || details.ageMax() > MAX_AGE) {
            throw new BusinessRuleException("Age range must be within " + MIN_AGE + "-" + MAX_AGE);
        }
        if (newInterests.isEmpty() || newInterests.size() > MAX_INTERESTS) {
            throw new BusinessRuleException("Choose from 1 to " + MAX_INTERESTS + " interests");
        }
        this.displayName = details.displayName().trim();
        this.birthDate = details.birthDate();
        this.gender = details.gender();
        this.lookingFor = details.lookingFor();
        this.ageMin = details.ageMin();
        this.ageMax = details.ageMax();
        this.city = normalizeCity(details.city());
        this.bio = details.bio() == null || details.bio().isBlank() ? null : details.bio().trim();
        this.contact = details.contact().trim();
        this.visible = details.visible();
        this.interests.clear();
        this.interests.addAll(newInterests);
    }

    /** Возраст на указанную дату. */
    public int age(LocalDate today) {
        return Period.between(birthDate, today).getYears();
    }

    public int getAge() {
        return age(LocalDate.now());
    }

    /** Подходит ли анкета {@code candidate} под настройки поиска этой анкеты (пол и возраст). */
    public boolean wants(Profile candidate, LocalDate today) {
        int candidateAge = candidate.age(today);
        return lookingFor.accepts(candidate.gender) && candidateAge >= ageMin && candidateAge <= ageMax;
    }

    /** Взаимная совместимость по настройкам поиска: каждый подходит другому. */
    public boolean isMutuallyCompatibleWith(Profile other, LocalDate today) {
        return this.wants(other, today) && other.wants(this, today);
    }

    public boolean isInSameCityAs(Profile other) {
        return city.equalsIgnoreCase(other.city);
    }

    /** Общие интересы с другой анкетой, по алфавиту. */
    public List<Interest> commonInterests(Profile other) {
        return interests.stream()
                .filter(other.interests::contains)
                .sorted(Comparator.comparing(Interest::getName))
                .toList();
    }

    /** Число общих интересов с другой анкетой. */
    public long countCommonInterests(Profile other) {
        return commonInterests(other).size();
    }

    static String normalizeCity(String city) {
        String trimmed = city.trim().replaceAll("\\s+", " ");
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }
}
