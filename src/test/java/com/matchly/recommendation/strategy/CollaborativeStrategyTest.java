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

class CollaborativeStrategyTest {

    private final CollaborativeStrategy strategy = new CollaborativeStrategy();

    @Test
    void recommendsWhatSimilarUsersLiked() {
        // зритель 1 лайкнул 10 и 11; пользователь 2 лайкнул 10, 11, 12 (похож); пользователь 3 лайкнул только 13
        RecommendationContext context = RecommendationContext.of(TODAY, Map.of(
                1L, Set.of(10L, 11L),
                2L, Set.of(10L, 11L, 12L),
                3L, Set.of(13L)));
        Profile viewer = profile(1, 30, "Moscow", 1);
        Profile twelve = profile(12, 30, "Moscow", 1);
        Profile thirteen = profile(13, 30, "Moscow", 1);

        List<ScoredCandidate> ranked = strategy.rank(viewer, List.of(twelve, thirteen), context);

        ScoredCandidate for12 = ranked.get(0);
        ScoredCandidate for13 = ranked.get(1);
        assertThat(for12.score()).isCloseTo(1.0, within(1e-9));   // единственный похожий пользователь лайкнул 12
        assertThat(for12.reasons()).containsExactly("Нравится 1 пользователю с похожими вкусами");
        assertThat(for13.score()).isZero();
        assertThat(for13.reasons()).isEmpty();
    }

    @Test
    void weightsSupportersByCosineSimilarity() {
        // пользователь 2 очень похож (2 общих из 2), пользователь 3 похож слабо (1 общий из 4)
        RecommendationContext context = RecommendationContext.of(TODAY, Map.of(
                1L, Set.of(10L, 11L),
                2L, Set.of(10L, 11L, 20L),
                3L, Set.of(10L, 30L, 31L, 32L, 21L)));
        Profile viewer = profile(1, 30, "Moscow", 1);
        Profile twenty = profile(20, 30, "Moscow", 1);
        Profile twentyOne = profile(21, 30, "Moscow", 1);

        List<ScoredCandidate> ranked = strategy.rank(viewer, List.of(twenty, twentyOne), context);

        double sim2 = 2 / Math.sqrt(2.0 * 3);
        double sim3 = 1 / Math.sqrt(2.0 * 5);
        assertThat(ranked.get(0).score()).isCloseTo(sim2 / (sim2 + sim3), within(1e-9));
        assertThat(ranked.get(1).score()).isCloseTo(sim3 / (sim2 + sim3), within(1e-9));
        assertThat(ranked.get(0).score()).isGreaterThan(ranked.get(1).score());
    }

    @Test
    void coldStart_viewerWithoutLikes_getsZeros() {
        RecommendationContext context = RecommendationContext.of(TODAY, Map.of(2L, Set.of(10L)));
        Profile viewer = profile(1, 30, "Moscow", 1);

        List<ScoredCandidate> ranked = strategy.rank(viewer, List.of(profile(10, 30, "Moscow", 1)), context);

        assertThat(ranked).singleElement().satisfies(s -> {
            assertThat(s.score()).isZero();
            assertThat(s.reasons()).isEmpty();
        });
    }

    @Test
    void similarities_ignoreViewerItself() {
        RecommendationContext context = RecommendationContext.of(TODAY, Map.of(1L, Set.of(10L), 2L, Set.of(10L)));

        Map<Long, Double> similarities = CollaborativeStrategy.similarities(1L, Set.of(10L), context);

        assertThat(similarities).containsOnlyKeys(2L);
        assertThat(similarities.get(2L)).isCloseTo(1.0, within(1e-9));
    }
}
