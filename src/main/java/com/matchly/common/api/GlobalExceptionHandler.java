package com.matchly.common.api;

import com.matchly.common.exception.MatchlyException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Единая точка обработки ошибок. Любое исключение превращается в ответ формата
 * RFC 9457 (application/problem+json) и записывается в лог с нужным уровнем.
 * Наследование от {@link ResponseEntityExceptionHandler} даёт готовую обработку
 * стандартных ошибок Spring MVC (неверный JSON, неподдерживаемый метод и т.п.).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Прикладные исключения: статус и заголовок берутся из самого исключения. */
    @ExceptionHandler(MatchlyException.class)
    public ProblemDetail handleMatchly(MatchlyException ex, HttpServletRequest request) {
        log.warn("{} {} -> {} {}", request.getMethod(), request.getRequestURI(),
                ex.getStatus().value(), ex.getMessage());
        return problem(ex.getStatus(), ex.getTitle(), ex.getMessage());
    }

    /** Нет или недействителен токен: сюда же делегируют entry point и access denied handler безопасности. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String detail = switch (ex) {
            case AccountStatusException e -> e.getMessage();
            case BadCredentialsException e -> e.getMessage();
            case InsufficientAuthenticationException e -> "Authentication is required";
            case OAuth2AuthenticationException e -> "Invalid or expired token";
            default -> "Authentication failed";
        };
        log.debug("{} {} -> 401 {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("{} {} -> 403 access denied", request.getMethod(), request.getRequestURI());
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Access is denied");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("{} {} -> 409 data integrity violation: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", "Request conflicts with existing data");
    }

    /** Последняя линия обороны: неожиданная ошибка логируется со стеком, клиенту уходит нейтральный ответ. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("{} {} -> 500 unexpected error", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected error occurred");
    }

    /** Ошибки валидации тела запроса: добавляем список полей с сообщениями. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", Objects.requireNonNullElse(fe.getDefaultMessage(), "invalid value")))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request contains invalid fields");
        pd.setProperty("errors", errors);
        log.debug("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
