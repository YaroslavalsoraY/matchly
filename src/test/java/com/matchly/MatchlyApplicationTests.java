package com.matchly;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Дымовой тест: контекст Spring поднимается на H2, миграции Flyway применяются без ошибок.
 */
@SpringBootTest
@ActiveProfiles("test")
class MatchlyApplicationTests {

    @Test
    void contextLoads() {
    }
}
