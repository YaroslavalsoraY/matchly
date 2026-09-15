package com.matchly.recommendation;

import com.matchly.reaction.LikeCount;
import com.matchly.reaction.LikeEdge;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Данные, общие для всех стратегий в рамках одного запроса: текущая дата,
 * матрица лайков («кто кого лайкнул») и число полученных лайков по анкетам.
 * Загружается один раз, чтобы стратегии не ходили в базу.
 */
public record RecommendationContext(LocalDate today,
                                    Map<Long, Set<Long>> likesBySource,
                                    Map<Long, Long> likesReceived) {

    public static RecommendationContext build(LocalDate today, Collection<LikeEdge> edges, Collection<LikeCount> counts) {
        Map<Long, Set<Long>> bySource = new HashMap<>();
        for (LikeEdge edge : edges) {
            bySource.computeIfAbsent(edge.sourceProfileId(), k -> new HashSet<>()).add(edge.targetProfileId());
        }
        Map<Long, Long> received = counts.stream().collect(Collectors.toMap(LikeCount::profileId, LikeCount::likes));
        return new RecommendationContext(today, Map.copyOf(bySource), Map.copyOf(received));
    }

    /** Кого лайкнула анкета. */
    public Set<Long> likesOf(Long profileId) {
        return likesBySource.getOrDefault(profileId, Set.of());
    }

    /** Сколько лайков получила анкета. */
    public long likesReceivedBy(Long profileId) {
        return likesReceived.getOrDefault(profileId, 0L);
    }

    public boolean hasLiked(Long sourceProfileId, Long targetProfileId) {
        return likesOf(sourceProfileId).contains(targetProfileId);
    }

    /** Пустой контекст (например, для тестов стратегий, которым лайки не нужны). */
    public static RecommendationContext empty(LocalDate today) {
        return new RecommendationContext(today, Map.of(), Map.of());
    }

    /** Удобный конструктор из «сырых» коллекций для тестов. */
    public static RecommendationContext of(LocalDate today, Map<Long, Set<Long>> likesBySource) {
        Map<Long, Long> received = likesBySource.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return new RecommendationContext(today, likesBySource, received);
    }
}
