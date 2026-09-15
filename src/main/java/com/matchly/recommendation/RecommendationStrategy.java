package com.matchly.recommendation;

import com.matchly.profile.Profile;

import java.util.List;

/**
 * Стратегия ранжирования кандидатов (паттерн «Стратегия»).
 * Реализации взаимозаменяемы: сервис выбирает нужную по {@link StrategyType} во время выполнения.
 */
public interface RecommendationStrategy {

    StrategyType type();

    /**
     * Оценивает кандидатов для зрителя. Порядок результата не важен, сервис сортирует сам.
     * Каждая оценка лежит в диапазоне 0..1, чтобы стратегии можно было комбинировать.
     */
    List<ScoredCandidate> rank(Profile viewer, List<Profile> candidates, RecommendationContext context);
}
