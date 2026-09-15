package com.matchly.common.exception;

import org.springframework.http.HttpStatus;

/** Действие запрещено текущему пользователю (403). */
public class ForbiddenException extends MatchlyException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "Forbidden", message);
    }
}
