package com.matchly.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Описание API для Swagger UI ({@code /swagger-ui.html}).
 * Все защищённые операции требуют заголовок {@code Authorization: Bearer <jwt>}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Matchly API", version = "v1",
                description = "Сервис знакомств с модулем рекомендаций: аккаунты, анкеты, реакции, матчи, рекомендации."),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Токен из ответа POST /api/auth/login или /api/auth/register")
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
}
