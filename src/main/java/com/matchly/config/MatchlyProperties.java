package com.matchly.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Типобезопасные настройки приложения (префикс {@code matchly} в application.yml).
 * Проверяются при старте: некорректная конфигурация останавливает запуск с понятным сообщением.
 */
@Validated
@ConfigurationProperties(prefix = "matchly")
public record MatchlyProperties(@Valid @NotNull Security security,
                                @Valid @NotNull Seed seed,
                                @Valid @NotNull Admin admin) {

    /** Параметры безопасности: секрет подписи и время жизни JWT. */
    public record Security(
            @NotBlank @Size(min = 32, message = "jwt-secret must be at least 32 characters long") String jwtSecret,
            @NotNull Duration jwtTtl) {
    }

    /** Генерация демонстрационных данных при старте. */
    public record Seed(boolean enabled, @Min(0) int profiles) {
    }

    /** Учётные данные администратора, создаваемого при первом запуске. */
    public record Admin(@NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {
    }
}
