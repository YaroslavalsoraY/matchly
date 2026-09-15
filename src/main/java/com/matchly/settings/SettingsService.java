package com.matchly.settings;

import com.matchly.recommendation.StrategyType;
import com.matchly.settings.dto.SettingsResponse;
import com.matchly.settings.dto.UpdateSettingsRequest;

/** Настройки, которые администратор меняет без перезапуска приложения. */
public interface SettingsService {

    /** Алгоритм рекомендаций, используемый, если пользователь не выбрал свой. */
    StrategyType getDefaultStrategy();

    SettingsResponse get();

    SettingsResponse update(UpdateSettingsRequest request);
}
