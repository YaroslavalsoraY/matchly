package com.matchly.recommendation.strategy;

import com.matchly.profile.Profile;
import com.matchly.recommendation.RecommendationContext;
import com.matchly.recommendation.ScoredCandidate;
import com.matchly.recommendation.StrategyType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Рекомендации по популярности: чем больше лайков получила анкета, тем выше оценка.
 * Простой и предсказуемый вариант, полезный как запасной при нехватке данных.
 */
@Component
public class PopularityStrategy extends AbstractRecommendationStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.POPULARITY;
    }

    @Override
    public List<ScoredCandidate> rank(Profile viewer, List<Profile> candidates, RecommendationContext context) {
        long max = candidates.stream().mapToLong(c -> context.likesReceivedBy(c.getId())).max().orElse(0L);
        return candidates.stream().map(candidate -> {
            long likes = context.likesReceivedBy(candidate.getId());
            double score = max == 0 ? 0.0 : (double) likes / max;
            List<String> reasons = likes == 0 ? List.of()
                    : List.of(plural(likes, "лайк", "лайка", "лайков") + " от других пользователей");
            return new ScoredCandidate(candidate, clamp(score), reasons);
        }).toList();
    }
}
