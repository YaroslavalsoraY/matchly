package com.matchly.settings;

import com.matchly.recommendation.StrategyType;
import com.matchly.settings.dto.SettingsResponse;
import com.matchly.settings.dto.UpdateSettingsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    static final String KEY_DEFAULT_STRATEGY = "default_strategy";
    static final StrategyType FALLBACK_STRATEGY = StrategyType.HYBRID;

    private final AppSettingRepository repository;

    @Override
    @Transactional(readOnly = true)
    public StrategyType getDefaultStrategy() {
        return repository.findById(KEY_DEFAULT_STRATEGY)
                .map(setting -> parseStrategy(setting.getValue()))
                .orElse(FALLBACK_STRATEGY);
    }

    @Override
    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return new SettingsResponse(getDefaultStrategy());
    }

    @Override
    @Transactional
    public SettingsResponse update(UpdateSettingsRequest request) {
        String value = request.defaultStrategy().name();
        AppSetting setting = repository.findById(KEY_DEFAULT_STRATEGY)
                .map(existing -> {
                    existing.update(value);
                    return existing;
                })
                .orElseGet(() -> repository.save(new AppSetting(KEY_DEFAULT_STRATEGY, value)));
        log.info("Default recommendation strategy changed to {}", setting.getValue());
        return new SettingsResponse(request.defaultStrategy());
    }

    private static StrategyType parseStrategy(String raw) {
        try {
            return StrategyType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown strategy '{}' in settings, falling back to {}", raw, FALLBACK_STRATEGY);
            return FALLBACK_STRATEGY;
        }
    }
}
