package com.matchly.security;

import com.matchly.user.Role;

/**
 * Принципал текущего запроса: минимум данных о пользователе, извлечённых из проверенного JWT.
 * Доступен в контроллерах через {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
