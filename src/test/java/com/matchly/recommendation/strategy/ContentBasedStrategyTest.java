package com.matchly.recommendation.strategy;

import com.matchly.profile.Profile;
import com.matchly.recommendation.RecommendationContext;
import com.matchly.recommendation.ScoredCandidate;
import com.matchly.recommendation.StrategyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.matchly.recommendation.strategy.StrategyTestData.TODAY;
import static com.matchly.recommendation.strategy.StrategyTestData.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ContentBasedStrategyTest {

    private final ContentBasedStrategy strategy = new ContentBasedStrategy();
    private final RecommendationContext context = RecommendationContext.empty(TODAY);

    @Test
    void identicalProfile_getsMaximumScore_withAllReasons() {
        Profile viewer = profile(1, 30, "Moscow", 1, 2, 3);
        Profile twin = profile(2, 30, "Moscow", 1, 2, 3);

        ScoredCandidate scored = strategy.score(viewer, twin, context);

        assertThat(scored.score()).isCloseTo(1.0, within(1e-9));
        assertThat(scored.reasons()).containsExactly("Общие интересы: Interest 1, Interest 2, Interest 3", "Тот же город", "Близкий возраст");
    }

    @Test
    void nothingInCommon_getsZero_andNoReasons() {
        Profile viewer = profile(1, 25, "Moscow", 1, 2);
        Profile stranger = profile(2, 45, "Kazan", 7, 8);

        ScoredCandidate scored = strategy.score(viewer, stranger, context);

        assertThat(scored.score()).isZero();
        assertThat(scored.reasons()).isEmpty();
    }

    @Test
    void weightsAreAppliedPerComponent() {
        Profile viewer = profile(1, 30, "Moscow", 1, 2, 3, 4);
        // 2 общих из 6 уникальных -> Жаккар 1/3; возраст совпадает; другой город
        Profile candidate = profile(2, 30, "Kazan", 3, 4, 5, 6);

        ScoredCandidate scored = strategy.score(viewer, candidate, context);

        double expected = ContentBasedStrategy.WEIGHT_INTERESTS * (2.0 / 6) + ContentBasedStrategy.WEIGHT_AGE * 1.0;
        assertThat(scored.score()).isCloseTo(expected, within(1e-9));
        assertThat(scored.reasons()).containsExactly("Общие интересы: Interest 3, Interest 4", "Близкий возраст");
    }

    @Test
    void ageDifferenceBeyondSpan_zeroesAgeComponent() {
        Profile viewer = profile(1, 20, "Moscow", 1);
        Profile older = profile(2, 40, "Moscow", 9);

        ScoredCandidate scored = strategy.score(viewer, older, context);

        assertThat(scored.score()).isCloseTo(ContentBasedStrategy.WEIGHT_CITY, within(1e-9));
        assertThat(scored.reasons()).containsExactly("Тот же город");
    }

    @Test
    void manyCommonInterests_areAbbreviated() {
        Profile viewer = profile(1, 30, "Moscow", 1, 2, 3, 4, 5);
        Profile candidate = profile(2, 30, "Moscow", 1, 2, 3, 4, 5);

        ScoredCandidate scored = strategy.score(viewer, candidate, context);

        assertThat(scored.reasons().get(0)).isEqualTo("Общие интересы: Interest 1, Interest 2, Interest 3 и ещё 2");
    }

    @Test
    void rank_scoresEveryCandidate_andReportsType() {
        Profile viewer = profile(1, 30, "Moscow", 1, 2);
        List<ScoredCandidate> ranked = strategy.rank(viewer,
                List.of(profile(2, 30, "Moscow", 1, 2), profile(3, 50, "Kazan", 9)), context);

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).score()).isGreaterThan(ranked.get(1).score());
        assertThat(strategy.type()).isEqualTo(StrategyType.CONTENT);
    }
}
