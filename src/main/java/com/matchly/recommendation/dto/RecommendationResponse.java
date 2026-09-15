package com.matchly.recommendation.dto;

import com.matchly.recommendation.StrategyType;

import java.util.List;

/** Подборка рекомендаций для текущего пользователя. */
public record RecommendationResponse(StrategyType strategy,
                                     String strategyTitle,
                                     int candidatesConsidered,
                                     List<RecommendationItem> items) {
}
