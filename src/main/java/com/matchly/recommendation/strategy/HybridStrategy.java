package com.matchly.recommendation.strategy;

import com.matchly.profile.Profile;
import com.matchly.recommendation.RecommendationContext;
import com.matchly.recommendation.ScoredCandidate;
import com.matchly.recommendation.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Гибрид (паттерн «Компоновщик» поверх стратегий): взвешенная сумма трёх базовых оценок
 * плюс бонус за проявленный интерес - если кандидат уже лайкнул зрителя, шанс матча выше.
 */
@Component
@RequiredArgsConstructor
public class HybridStrategy extends AbstractRecommendationStrategy {

    static final double WEIGHT_CONTENT = 0.5;
    static final double WEIGHT_COLLABORATIVE = 0.3;
    static final double WEIGHT_POPULARITY = 0.2;
    static final double LIKED_YOU_BONUS = 0.15;
    static final String LIKED_YOU_REASON = "Проявил(а) интерес к вам";

    private final ContentBasedStrategy contentBased;
    private final CollaborativeStrategy collaborative;
    private final PopularityStrategy popularity;

    @Override
    public StrategyType type() {
        return StrategyType.HYBRID;
    }

    @Override
    public List<ScoredCandidate> rank(Profile viewer, List<Profile> candidates, RecommendationContext context) {
        Map<Long, ScoredCandidate> content = byProfile(contentBased.rank(viewer, candidates, context));
        Map<Long, ScoredCandidate> collab = byProfile(collaborative.rank(viewer, candidates, context));
        Map<Long, ScoredCandidate> popular = byProfile(popularity.rank(viewer, candidates, context));

        return candidates.stream().map(candidate -> {
            Long id = candidate.getId();
            double score = WEIGHT_CONTENT * content.get(id).score()
                    + WEIGHT_COLLABORATIVE * collab.get(id).score()
                    + WEIGHT_POPULARITY * popular.get(id).score();
            LinkedHashSet<String> reasons = new LinkedHashSet<>();
            reasons.addAll(content.get(id).reasons());
            reasons.addAll(collab.get(id).reasons());
            reasons.addAll(popular.get(id).reasons());
            if (context.hasLiked(id, viewer.getId())) {
                score += LIKED_YOU_BONUS;
                reasons.add(LIKED_YOU_REASON);
            }
            return new ScoredCandidate(candidate, clamp(score), new ArrayList<>(reasons));
        }).toList();
    }

    private static Map<Long, ScoredCandidate> byProfile(List<ScoredCandidate> scored) {
        return scored.stream().collect(Collectors.toMap(s -> s.profile().getId(), Function.identity()));
    }
}
