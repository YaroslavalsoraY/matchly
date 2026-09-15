package com.matchly.recommendation;

import com.matchly.profile.Profile;

import java.util.List;

/** Кандидат с оценкой в диапазоне 0..1 и человекочитаемыми причинами, почему он подходит. */
public record ScoredCandidate(Profile profile, double score, List<String> reasons) {

    public static ScoredCandidate zero(Profile profile) {
        return new ScoredCandidate(profile, 0.0, List.of());
    }
}
