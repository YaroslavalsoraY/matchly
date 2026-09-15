package com.matchly.recommendation.dto;

import com.matchly.recommendation.StrategyType;

/** Описание алгоритма для переключателя в интерфейсе. */
public record StrategyInfo(StrategyType type, String title, String description, boolean isDefault) {
}
