package com.matchly.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Корень иерархии прикладных исключений. Каждое знает свой HTTP-статус и заголовок,
 * поэтому обработчик ошибок превращает любое из них в ProblemDetail единообразно (полиморфизм).
 */
@Getter
public abstract class MatchlyException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected MatchlyException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }
}
