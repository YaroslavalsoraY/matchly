package com.matchly.profile;

import com.matchly.common.exception.BusinessRuleException;
import com.matchly.interest.Interest;
import com.matchly.interest.InterestCategory;
import com.matchly.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Правила предметной области анкеты без Spring и базы. */
class ProfileTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    @Test
    void age_isComputedAgainstGivenDate() {
        Profile profile = profileOf(LocalDate.of(2000, 9, 16), Gender.FEMALE, Preference.MALE, 18, 40);
        assertThat(profile.age(TODAY)).isEqualTo(25);
        assertThat(profile.age(TODAY.plusDays(1))).isEqualTo(26);
    }

    @Test
    void update_rejectsUnderage() {
        Profile profile = new Profile(User.register("u@test.local", "hash"));
        ProfileDetails details = details(TODAY.minusYears(18).plusDays(1), Gender.MALE, Preference.FEMALE, 18, 30);

        assertThatThrownBy(() -> profile.update(details, Set.of(interest(1L)), TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 18");
    }

    @Test
    void update_rejectsInvertedAgeRange_andEmptyInterests() {
        Profile profile = new Profile(User.register("u@test.local", "hash"));

        assertThatThrownBy(() -> profile.update(details(LocalDate.of(1990, 1, 1), Gender.MALE, Preference.FEMALE, 35, 30),
                Set.of(interest(1L)), TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Minimum age");

        assertThatThrownBy(() -> profile.update(details(LocalDate.of(1990, 1, 1), Gender.MALE, Preference.FEMALE, 20, 30),
                Set.of(), TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("interests");
    }

    @Test
    void update_normalizesTextFields() {
        Profile profile = new Profile(User.register("u@test.local", "hash"));
        ProfileDetails details = new ProfileDetails("  Ivan ", LocalDate.of(1990, 1, 1), Gender.MALE, Preference.FEMALE,
                20, 30, "  saint   petersburg ", "   ", " @ivan ", true);

        profile.update(details, Set.of(interest(1L)), TODAY);

        assertThat(profile.getDisplayName()).isEqualTo("Ivan");
        assertThat(profile.getCity()).isEqualTo("Saint petersburg");
        assertThat(profile.getBio()).isNull();
        assertThat(profile.getContact()).isEqualTo("@ivan");
    }

    @Test
    void wants_checksGenderPreferenceAndAgeRange() {
        Profile anna = profileOf(LocalDate.of(1996, 1, 1), Gender.FEMALE, Preference.MALE, 25, 35);      // 30 лет
        Profile bob = profileOf(LocalDate.of(1993, 1, 1), Gender.MALE, Preference.FEMALE, 20, 32);       // 33 года
        Profile tooOld = profileOf(LocalDate.of(1980, 1, 1), Gender.MALE, Preference.EVERYONE, 18, 99);  // 46 лет
        Profile kate = profileOf(LocalDate.of(1996, 1, 1), Gender.FEMALE, Preference.EVERYONE, 18, 99);

        assertThat(anna.wants(bob, TODAY)).isTrue();
        assertThat(bob.wants(anna, TODAY)).isTrue();
        assertThat(anna.isMutuallyCompatibleWith(bob, TODAY)).isTrue();
        assertThat(anna.wants(tooOld, TODAY)).isFalse();
        assertThat(anna.wants(kate, TODAY)).isFalse();      // Анна ищет мужчин
        assertThat(kate.wants(anna, TODAY)).isTrue();       // Кате подходят все
        assertThat(anna.isMutuallyCompatibleWith(kate, TODAY)).isFalse();
    }

    @Test
    void commonInterests_andSameCity() {
        Profile a = profileOf(LocalDate.of(1996, 1, 1), Gender.FEMALE, Preference.MALE, 18, 99);
        Profile b = profileOf(LocalDate.of(1996, 1, 1), Gender.MALE, Preference.FEMALE, 18, 99);
        ReflectionTestUtils.setField(a, "interests", new HashSet<>(Set.of(interest(1L), interest(2L), interest(3L))));
        ReflectionTestUtils.setField(b, "interests", new HashSet<>(Set.of(interest(2L), interest(3L), interest(4L))));
        ReflectionTestUtils.setField(b, "city", "MOSCOW");

        assertThat(a.countCommonInterests(b)).isEqualTo(2);
        assertThat(a.isInSameCityAs(b)).isTrue();
    }

    @Test
    void preference_accepts() {
        assertThat(Preference.EVERYONE.accepts(Gender.OTHER)).isTrue();
        assertThat(Preference.MALE.accepts(Gender.MALE)).isTrue();
        assertThat(Preference.MALE.accepts(Gender.FEMALE)).isFalse();
        assertThat(Preference.FEMALE.accepts(Gender.OTHER)).isFalse();
    }

    private static Profile profileOf(LocalDate birthDate, Gender gender, Preference lookingFor, int ageMin, int ageMax) {
        Profile profile = new Profile(User.register("u@test.local", "hash"));
        profile.update(details(birthDate, gender, lookingFor, ageMin, ageMax), Set.of(interest(1L)), TODAY);
        return profile;
    }

    private static ProfileDetails details(LocalDate birthDate, Gender gender, Preference lookingFor, int ageMin, int ageMax) {
        return new ProfileDetails("Name", birthDate, gender, lookingFor, ageMin, ageMax, "Moscow", "bio", "@contact", true);
    }

    private static Interest interest(Long id) {
        Interest interest = new Interest("i" + id, InterestCategory.HOBBY);
        ReflectionTestUtils.setField(interest, "id", id);
        return interest;
    }
}
