package com.matchly.recommendation;

import com.matchly.recommendation.dto.RecommendationResponse;
import com.matchly.recommendation.dto.StrategyInfo;

import java.util.List;

/** Персональные рекомендации анкет. */
public interface RecommendationService {

    /**
     * Подбирает анкеты для пользователя.
     *
     * @param strategy алгоритм; {@code null} означает алгоритм по умолчанию из настроек
     * @param limit    сколько карточек вернуть
     */
    RecommendationResponse recommend(Long userId, StrategyType strategy, int limit);

    List<StrategyInfo> strategies();
}
