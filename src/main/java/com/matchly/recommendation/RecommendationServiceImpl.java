package com.matchly.recommendation;

import com.matchly.common.exception.NotFoundException;
import com.matchly.profile.Profile;
import com.matchly.profile.ProfileCardAssembler;
import com.matchly.profile.ProfileRepository;
import com.matchly.profile.dto.ProfileCardResponse;
import com.matchly.reaction.ReactionRepository;
import com.matchly.reaction.ReactionType;
import com.matchly.recommendation.dto.RecommendationItem;
import com.matchly.recommendation.dto.RecommendationResponse;
import com.matchly.recommendation.dto.StrategyInfo;
import com.matchly.settings.SettingsService;
import com.matchly.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Конвейер рекомендаций: отбор кандидатов по жёстким правилам (видимость, взаимные предпочтения,
 * ещё не оценённые) -> оценка выбранной стратегией -> сортировка -> сборка карточек.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final ProfileRepository profileRepository;
    private final ReactionRepository reactionRepository;
    private final StrategyRegistry registry;
    private final SettingsService settingsService;
    private final ProfileCardAssembler cardAssembler;

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, StrategyType requested, int limit) {
        Profile viewer = profileRepository.findWithInterestsByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        StrategyType type = requested != null ? requested : settingsService.getDefaultStrategy();
        RecommendationStrategy strategy = registry.get(type);
        LocalDate today = LocalDate.now();

        List<Profile> candidates = selectCandidates(viewer, today);
        RecommendationContext context = RecommendationContext.build(today,
                reactionRepository.findEdgesByType(ReactionType.LIKE),
                reactionRepository.countByTargetGroupedByType(ReactionType.LIKE));

        List<ScoredCandidate> ranked = strategy.rank(viewer, candidates, context).stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(s -> s.profile().getCreatedAt(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        List<ProfileCardResponse> cards = cardAssembler.toCards(ranked.stream().map(ScoredCandidate::profile).toList());
        List<RecommendationItem> items = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            ScoredCandidate scored = ranked.get(i);
            items.add(new RecommendationItem(cards.get(i), round(scored.score()), scored.reasons()));
        }
        log.debug("Recommendations for profile {}: strategy={}, candidates={}, returned={}",
                viewer.getId(), type, candidates.size(), items.size());
        return new RecommendationResponse(type, type.getTitle(), candidates.size(), items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StrategyInfo> strategies() {
        StrategyType defaultType = settingsService.getDefaultStrategy();
        return Arrays.stream(StrategyType.values())
                .map(t -> new StrategyInfo(t, t.getTitle(), t.getDescription(), t == defaultType))
                .toList();
    }

    /** Жёсткие фильтры, не зависящие от стратегии. */
    private List<Profile> selectCandidates(Profile viewer, LocalDate today) {
        Set<Long> alreadyReacted = reactionRepository.findTargetIdsBySourceId(viewer.getId());
        return profileRepository.findAllVisible(UserStatus.ACTIVE).stream()
                .filter(candidate -> !candidate.getId().equals(viewer.getId()))
                .filter(candidate -> !alreadyReacted.contains(candidate.getId()))
                .filter(candidate -> viewer.isMutuallyCompatibleWith(candidate, today))
                .toList();
    }

    private static double round(double score) {
        return Math.round(score * 1000.0) / 1000.0;
    }
}
