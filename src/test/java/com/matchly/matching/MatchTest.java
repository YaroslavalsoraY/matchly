package com.matchly.matching;

import com.matchly.profile.Profile;
import com.matchly.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    @Test
    void between_ordersProfilesById() {
        Profile p7 = profile(7L);
        Profile p3 = profile(3L);

        Match match = Match.between(p7, p3);

        assertThat(match.getProfileA()).isSameAs(p3);
        assertThat(match.getProfileB()).isSameAs(p7);
        assertThat(match.involves(3L)).isTrue();
        assertThat(match.involves(8L)).isFalse();
    }

    @Test
    void other_returnsCounterpart_andRejectsOutsider() {
        Match match = Match.between(profile(1L), profile(2L));

        assertThat(match.other(1L).getId()).isEqualTo(2L);
        assertThat(match.other(2L).getId()).isEqualTo(1L);
        assertThatThrownBy(() -> match.other(5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void between_rejectsSameProfile() {
        Profile p = profile(1L);
        assertThatThrownBy(() -> Match.between(p, p)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Profile profile(Long id) {
        Profile profile = new Profile(User.register("u" + id + "@test.local", "hash"));
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }
}
