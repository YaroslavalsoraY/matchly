package com.matchly.common.exception;

import org.springframework.http.HttpStatus;

/** Конфликт с текущим состоянием данных, например дублирующийся email (409). */
public class ConflictException extends MatchlyException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "Conflict", message);
    }
}
