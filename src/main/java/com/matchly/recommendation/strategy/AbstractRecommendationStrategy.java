package com.matchly.recommendation.strategy;

import com.matchly.recommendation.RecommendationStrategy;

/** Общие вспомогательные методы стратегий: нормализация оценок и русские формы множественного числа. */
public abstract class AbstractRecommendationStrategy implements RecommendationStrategy {

    protected static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Возвращает число с правильной формой слова: 1 лайк, 2 лайка, 5 лайков. */
    protected static String plural(long n, String one, String few, String many) {
        long mod10 = n % 10;
        long mod100 = n % 100;
        String word;
        if (mod10 == 1 && mod100 != 11) {
            word = one;
        } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            word = few;
        } else {
            word = many;
        }
        return n + " " + word;
    }
}
