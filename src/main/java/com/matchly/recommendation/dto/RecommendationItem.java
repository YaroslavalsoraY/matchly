package com.matchly.recommendation.dto;

import com.matchly.profile.dto.ProfileCardResponse;

import java.util.List;

/** Одна рекомендация: карточка, оценка 0..1 и причины показа. */
public record RecommendationItem(ProfileCardResponse profile, double score, List<String> reasons) {
}
