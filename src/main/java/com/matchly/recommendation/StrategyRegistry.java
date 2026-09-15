package com.matchly.recommendation;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр стратегий: Spring собирает все реализации {@link RecommendationStrategy},
 * а реестр отдаёт нужную по типу. Добавление новой стратегии не требует правок сервиса.
 */
@Component
public class StrategyRegistry {

    private final Map<StrategyType, RecommendationStrategy> strategies = new EnumMap<>(StrategyType.class);

    public StrategyRegistry(List<RecommendationStrategy> available) {
        for (RecommendationStrategy strategy : available) {
            RecommendationStrategy previous = strategies.put(strategy.type(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate strategy for type " + strategy.type());
            }
        }
        for (StrategyType type : StrategyType.values()) {
            if (!strategies.containsKey(type)) {
                throw new IllegalStateException("No strategy registered for type " + type);
            }
        }
    }

    public RecommendationStrategy get(StrategyType type) {
        return strategies.get(type);
    }
}
