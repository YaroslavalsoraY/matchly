package com.matchly.settings.dto;

import com.matchly.recommendation.StrategyType;

/** Текущие настройки приложения. */
public record SettingsResponse(StrategyType defaultStrategy) {
}
