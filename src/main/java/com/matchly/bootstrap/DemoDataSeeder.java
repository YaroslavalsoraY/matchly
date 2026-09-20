package com.matchly.bootstrap;

import com.matchly.config.MatchlyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Запускает генерацию демо-данных при старте, если она включена настройкой matchly.seed.enabled.
 * Ошибка генерации не останавливает приложение: она записывается в лог, а данные можно создать позже
 * через POST /api/admin/demo-data или кнопку в настройках администратора.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final MatchlyProperties properties;
    private final DemoDataService demoDataService;

    @Override
    public void run(ApplicationArguments args) {
        MatchlyProperties.Seed seed = properties.seed();
        log.info("Demo data seeding on startup: enabled={}, profiles={}", seed.enabled(), seed.profiles());
        if (!seed.enabled() || seed.profiles() == 0) {
            log.info("Demo data seeding is disabled");
            return;
        }
        try {
            log.info("Demo data seeding result: {}", demoDataService.seed());
        } catch (RuntimeException e) {
            log.error("Demo data seeding failed, application continues without demo data. "
                    + "Use POST /api/admin/demo-data to retry", e);
        }
    }
}
