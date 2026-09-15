package com.matchly.recommendation.strategy;

import com.matchly.interest.Interest;
import com.matchly.profile.Profile;
import com.matchly.recommendation.RecommendationContext;
import com.matchly.recommendation.ScoredCandidate;
import com.matchly.recommendation.StrategyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Рекомендации по содержимому анкет: сходство интересов (коэффициент Жаккара),
 * близость возраста и совпадение города. Работает даже для новых пользователей без лайков.
 */
@Component
public class ContentBasedStrategy extends AbstractRecommendationStrategy {

    static final double WEIGHT_INTERESTS = 0.6;
    static final double WEIGHT_AGE = 0.25;
    static final double WEIGHT_CITY = 0.15;
    /** Разница в возрасте, при которой возрастная составляющая обнуляется. */
    static final int AGE_SPAN_YEARS = 15;
    static final int CLOSE_AGE_YEARS = 3;
    private static final int MAX_LISTED_INTERESTS = 3;

    @Override
    public StrategyType type() {
        return StrategyType.CONTENT;
    }

    @Override
    public List<ScoredCandidate> rank(Profile viewer, List<Profile> candidates, RecommendationContext context) {
        return candidates.stream().map(candidate -> score(viewer, candidate, context)).toList();
    }

    ScoredCandidate score(Profile viewer, Profile candidate, RecommendationContext context) {
        List<Interest> common = viewer.commonInterests(candidate);
        int union = viewer.getInterests().size() + candidate.getInterests().size() - common.size();
        double interestScore = union == 0 ? 0.0 : (double) common.size() / union;

        int ageDiff = Math.abs(viewer.age(context.today()) - candidate.age(context.today()));
        double ageScore = Math.max(0.0, 1.0 - (double) ageDiff / AGE_SPAN_YEARS);

        boolean sameCity = viewer.isInSameCityAs(candidate);
        double cityScore = sameCity ? 1.0 : 0.0;

        double total = WEIGHT_INTERESTS * interestScore + WEIGHT_AGE * ageScore + WEIGHT_CITY * cityScore;

        List<String> reasons = new ArrayList<>();
        if (!common.isEmpty()) {
            reasons.add("Общие интересы: " + describe(common));
        }
        if (sameCity) {
            reasons.add("Тот же город");
        }
        if (ageDiff <= CLOSE_AGE_YEARS) {
            reasons.add("Близкий возраст");
        }
        return new ScoredCandidate(candidate, clamp(total), List.copyOf(reasons));
    }

    private static String describe(List<Interest> common) {
        String listed = common.stream().limit(MAX_LISTED_INTERESTS).map(Interest::getName).collect(Collectors.joining(", "));
        int rest = common.size() - MAX_LISTED_INTERESTS;
        return rest > 0 ? listed + " и ещё " + rest : listed;
    }
}
