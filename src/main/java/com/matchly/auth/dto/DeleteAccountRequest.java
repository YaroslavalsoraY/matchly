package com.matchly.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Подтверждение удаления аккаунта текущим паролем. */
public record DeleteAccountRequest(@NotBlank String password) {
}
