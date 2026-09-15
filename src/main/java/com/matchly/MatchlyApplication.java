package com.matchly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа Matchly: сервис знакомств с модулем персональных рекомендаций.
 *
 * <p>Структура пакетов:
 * <ul>
 *   <li>{@code config} - конфигурация (настройки, JWT, OpenAPI);</li>
 *   <li>{@code common} - базовые классы: сущность, исключения, обработчик ошибок, DTO;</li>
 *   <li>{@code security} - фильтр безопасности, выдача и разбор JWT;</li>
 *   <li>{@code user}, {@code auth}, {@code admin} - учётные записи, вход, администрирование;</li>
 *   <li>{@code profile}, {@code reaction}, {@code recommendation} - предметная область знакомств.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MatchlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchlyApplication.class, args);
    }
}
