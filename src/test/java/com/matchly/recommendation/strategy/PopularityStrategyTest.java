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

class PopularityStrategyTest {

    private final PopularityStrategy strategy = new PopularityStrategy();

    @Test
    void normalizesByMostPopularCandidate() {
        // 10 получил 4 лайка, 11 - 2, 12 - ни одного
        RecommendationContext context = RecommendationContext.of(TODAY, Map.of(
                1L, Set.of(10L, 11L),
                2L, Set.of(10L, 11L),
                3L, Set.of(10L),
                4L, Set.of(10L)));
        Profile viewer = profile(99, 30, "Moscow", 1);

        List<ScoredCandidate> ranked = strategy.rank(viewer,
                List.of(profile(10, 30, "Moscow", 1), profile(11, 30, "Moscow", 1), profile(12, 30, "Moscow", 1)), context);

        assertThat(ranked.get(0).score()).isCloseTo(1.0, within(1e-9));
        assertThat(ranked.get(0).reasons()).containsExactly("4 лайка от других пользователей");
        assertThat(ranked.get(1).score()).isCloseTo(0.5, within(1e-9));
        assertThat(ranked.get(1).reasons()).containsExactly("2 лайка от других пользователей");
        assertThat(ranked.get(2).score()).isZero();
        assertThat(ranked.get(2).reasons()).isEmpty();
    }

    @Test
    void noLikesAnywhere_allZero() {
        Profile viewer = profile(1, 30, "Moscow", 1);
        List<ScoredCandidate> ranked = strategy.rank(viewer, List.of(profile(2, 30, "Moscow", 1)), RecommendationContext.empty(TODAY));
        assertThat(ranked.get(0).score()).isZero();
    }

    @Test
    void russianPluralForms() {
        assertThat(AbstractRecommendationStrategy.plural(1, "лайк", "лайка", "лайков")).isEqualTo("1 лайк");
        assertThat(AbstractRecommendationStrategy.plural(3, "лайк", "лайка", "лайков")).isEqualTo("3 лайка");
        assertThat(AbstractRecommendationStrategy.plural(11, "лайк", "лайка", "лайков")).isEqualTo("11 лайков");
        assertThat(AbstractRecommendationStrategy.plural(21, "лайк", "лайка", "лайков")).isEqualTo("21 лайк");
        assertThat(AbstractRecommendationStrategy.plural(114, "лайк", "лайка", "лайков")).isEqualTo("114 лайков");
    }
}
