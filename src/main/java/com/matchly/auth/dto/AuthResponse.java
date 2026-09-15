package com.matchly.auth.dto;

import com.matchly.user.dto.UserResponse;

import java.time.Instant;

/** Ответ на регистрацию и вход: JWT, срок его действия и краткие данные аккаунта. */
public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
