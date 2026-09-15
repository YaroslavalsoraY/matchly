package com.matchly.matching;

import com.matchly.common.entity.BaseEntity;
import com.matchly.profile.Profile;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Взаимная симпатия двух анкет. Пара хранится упорядоченной по идентификатору,
 * чтобы матч A-B и B-A был одной и той же записью.
 */
@Entity
@Table(name = "profile_matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_a_id", nullable = false)
    private Profile profileA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_b_id", nullable = false)
    private Profile profileB;

    private Match(Profile profileA, Profile profileB) {
        this.profileA = profileA;
        this.profileB = profileB;
    }

    public static Match between(Profile first, Profile second) {
        if (first.getId().equals(second.getId())) {
            throw new IllegalArgumentException("A profile cannot match itself");
        }
        return first.getId() < second.getId() ? new Match(first, second) : new Match(second, first);
    }

    public boolean involves(Long profileId) {
        return profileA.getId().equals(profileId) || profileB.getId().equals(profileId);
    }

    /** Собеседник для указанного участника матча. */
    public Profile other(Long profileId) {
        if (profileA.getId().equals(profileId)) {
            return profileB;
        }
        if (profileB.getId().equals(profileId)) {
            return profileA;
        }
        throw new IllegalArgumentException("Profile " + profileId + " is not part of this match");
    }
}
