package com.matchly.common.exception;

import org.springframework.http.HttpStatus;

/** Неверная пара email/пароль (401). Сообщение намеренно не уточняет, что именно неверно. */
public class InvalidCredentialsException extends MatchlyException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }
}
