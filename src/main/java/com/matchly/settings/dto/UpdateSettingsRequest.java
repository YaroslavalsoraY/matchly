package com.matchly.settings.dto;

import com.matchly.recommendation.StrategyType;
import jakarta.validation.constraints.NotNull;

/** Изменение настроек администратором. */
public record UpdateSettingsRequest(@NotNull StrategyType defaultStrategy) {
}
