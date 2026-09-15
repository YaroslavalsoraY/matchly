package com.matchly.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Данные для входа. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
