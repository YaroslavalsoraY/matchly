package com.matchly.recommendation.strategy;

import com.matchly.interest.Interest;
import com.matchly.interest.InterestCategory;
import com.matchly.profile.Gender;
import com.matchly.profile.Preference;
import com.matchly.profile.Profile;
import com.matchly.profile.ProfileDetails;
import com.matchly.user.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Фабрика анкет и интересов для unit-тестов стратегий. */
final class StrategyTestData {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    private StrategyTestData() {
    }

    static Profile profile(long id, int age, String city, long... interestIds) {
        Profile profile = new Profile(User.register("u" + id + "@test.local", "hash"));
        ProfileDetails details = new ProfileDetails("P" + id, TODAY.minusYears(age), Gender.OTHER, Preference.EVERYONE,
                18, 99, city, null, "@p" + id, true);
        Set<Interest> interests = Arrays.stream(interestIds).mapToObj(StrategyTestData::interest)
                .collect(Collectors.toCollection(HashSet::new));
        profile.update(details, interests, TODAY);
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    static Interest interest(long id) {
        Interest interest = new Interest("Interest " + id, InterestCategory.HOBBY);
        ReflectionTestUtils.setField(interest, "id", id);
        return interest;
    }
}
