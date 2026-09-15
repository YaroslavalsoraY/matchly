package com.matchly.user.dto;

import com.matchly.user.Role;
import com.matchly.user.UserStatus;

import java.time.Instant;

/** Публичное представление аккаунта (без хэша пароля). */
public record UserResponse(Long id, String email, Role role, UserStatus status, Instant createdAt) {
}
