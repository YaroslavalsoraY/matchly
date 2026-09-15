package com.matchly.recommendation.strategy;

import com.matchly.profile.Profile;
import com.matchly.recommendation.RecommendationContext;
import com.matchly.recommendation.ScoredCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.matchly.recommendation.strategy.StrategyTestData.TODAY;
import static com.matchly.recommendation.strategy.StrategyTestData.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HybridStrategyTest {

    private final HybridStrategy strategy = new HybridStrategy(new ContentBasedStrategy(), new CollaborativeStrategy(), new PopularityStrategy());

    @Test
    void combinesWeightedComponents_andAddsLikedYouBonus() {
        Profile viewer = profile(1, 30, "Moscow", 1, 2);
        Profile twin = profile(2, 30, "Moscow", 1, 2);          // контент = 1.0, лайкнул зрителя
        Profile stranger = profile(3, 50, "Kazan", 9);          // контент = 0
        // 2 лайкнул зрителя (1); зритель никого не лайкал -> коллаборативная = 0; популярность: 1 получил лайк, кандидаты нет
        RecommendationContext context = RecommendationContext.of(TODAY, Map.of(2L, Set.of(1L)));

        List<ScoredCandidate> ranked = strategy.rank(viewer, List.of(twin, stranger), context);

        ScoredCandidate forTwin = ranked.get(0);
        assertThat(forTwin.score()).isCloseTo(HybridStrategy.WEIGHT_CONTENT * 1.0 + HybridStrategy.LIKED_YOU_BONUS, within(1e-9));
        assertThat(forTwin.reasons()).contains("Тот же город", "Близкий возраст", HybridStrategy.LIKED_YOU_REASON);
        assertThat(ranked.get(1).score()).isZero();
        assertThat(ranked.get(1).reasons()).isEmpty();
    }

    @Test
    void scoreNeverExceedsOne() {
        Profile viewer = profile(1, 30, "Moscow", 1);
        Profile twin = profile(2, 30, "Moscow", 1);
        // зритель и пользователь 3 лайкали 2 -> коллаборативная 1.0; популярность 1.0; контент 1.0; бонус за лайк
        RecommendationContext context = RecommendationContext.of(TODAY, Map.of(
                1L, Set.of(5L), 3L, Set.of(5L, 2L), 2L, Set.of(1L)));

        ScoredCandidate scored = strategy.rank(viewer, List.of(twin), context).get(0);

        assertThat(scored.score()).isEqualTo(1.0);
    }
}
