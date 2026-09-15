package com.matchly.recommendation;

import lombok.Getter;

/** Доступные алгоритмы рекомендаций. Названия и описания показываются в интерфейсе. */
@Getter
public enum StrategyType {
    CONTENT("По интересам", "Общие интересы, близкий возраст и тот же город"),
    COLLABORATIVE("Похожие вкусы", "Кого лайкали пользователи с похожими на ваши лайками"),
    POPULARITY("Популярные", "Анкеты, получившие больше всего лайков"),
    HYBRID("Умная подборка", "Сочетание трёх подходов плюс учёт взаимного интереса");

    private final String title;
    private final String description;

    StrategyType(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
