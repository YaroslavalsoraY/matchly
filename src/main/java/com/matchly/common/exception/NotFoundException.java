package com.matchly.common.exception;

import org.springframework.http.HttpStatus;

/** Запрошенный ресурс не существует (404). */
public class NotFoundException extends MatchlyException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "Not Found", message);
    }

    public NotFoundException(String entity, Object id) {
        this(entity + " with id " + id + " not found");
    }
}
