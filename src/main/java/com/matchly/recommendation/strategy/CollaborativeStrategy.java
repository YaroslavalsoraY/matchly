package com.matchly.recommendation.strategy;

import com.matchly.profile.Profile;
import com.matchly.recommendation.RecommendationContext;
import com.matchly.recommendation.ScoredCandidate;
import com.matchly.recommendation.StrategyType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Коллаборативная фильтрация (user-based): ищем пользователей с похожими лайками
 * (косинусная близость множеств лайков) и рекомендуем то, что понравилось им.
 * Для пользователя без лайков стратегия ничего не знает и возвращает нули (холодный старт).
 */
@Component
public class CollaborativeStrategy extends AbstractRecommendationStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.COLLABORATIVE;
    }

    @Override
    public List<ScoredCandidate> rank(Profile viewer, List<Profile> candidates, RecommendationContext context) {
        Set<Long> myLikes = context.likesOf(viewer.getId());
        if (myLikes.isEmpty()) {
            return candidates.stream().map(ScoredCandidate::zero).toList();
        }
        Map<Long, Double> similarity = similarities(viewer.getId(), myLikes, context);
        double similaritySum = similarity.values().stream().mapToDouble(Double::doubleValue).sum();

        return candidates.stream().map(candidate -> {
            double weighted = 0.0;
            int supporters = 0;
            for (Map.Entry<Long, Double> entry : similarity.entrySet()) {
                if (context.hasLiked(entry.getKey(), candidate.getId())) {
                    weighted += entry.getValue();
                    supporters++;
                }
            }
            double score = similaritySum == 0.0 ? 0.0 : weighted / similaritySum;
            List<String> reasons = supporters == 0 ? List.of()
                    : List.of("Нравится " + plural(supporters, "пользователю", "пользователям", "пользователям")
                    + " с похожими вкусами");
            return new ScoredCandidate(candidate, clamp(score), reasons);
        }).toList();
    }

    /** Косинусная близость зрителя с каждым, у кого есть хотя бы один общий лайк. */
    static Map<Long, Double> similarities(Long viewerId, Set<Long> myLikes, RecommendationContext context) {
        Map<Long, Double> result = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> other : context.likesBySource().entrySet()) {
            if (other.getKey().equals(viewerId)) {
                continue;
            }
            Set<Long> theirLikes = other.getValue();
            long common = myLikes.stream().filter(theirLikes::contains).count();
            if (common > 0) {
                result.put(other.getKey(), common / Math.sqrt((double) myLikes.size() * theirLikes.size()));
            }
        }
        return result;
    }
}
