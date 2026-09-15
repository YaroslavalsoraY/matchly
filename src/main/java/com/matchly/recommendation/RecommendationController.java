package com.matchly.recommendation;

import com.matchly.recommendation.dto.RecommendationResponse;
import com.matchly.recommendation.dto.StrategyInfo;
import com.matchly.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Recommendations", description = "Персональная подборка анкет")
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private static final int MAX_LIMIT = 50;

    private final RecommendationService recommendationService;

    @Operation(summary = "Подборка анкет для меня",
            description = "Без параметра strategy используется алгоритм по умолчанию из настроек администратора")
    @GetMapping
    public RecommendationResponse recommend(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Parameter(description = "CONTENT, COLLABORATIVE, POPULARITY или HYBRID")
                                            @RequestParam(required = false) StrategyType strategy,
                                            @RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return recommendationService.recommend(user.id(), strategy, safeLimit);
    }

    @Operation(summary = "Доступные алгоритмы с описанием")
    @GetMapping("/strategies")
    public List<StrategyInfo> strategies() {
        return recommendationService.strategies();
    }
}
