package com.matchly.auth;

import com.matchly.auth.dto.AuthResponse;
import com.matchly.auth.dto.DeleteAccountRequest;
import com.matchly.auth.dto.LoginRequest;
import com.matchly.auth.dto.RegisterRequest;
import com.matchly.security.AuthenticatedUser;
import com.matchly.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Регистрация, вход и текущий пользователь. */
@Tag(name = "Auth", description = "Регистрация и вход по email и паролю, выдача JWT")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Зарегистрировать аккаунт", description = "Создаёт пользователя с ролью USER и возвращает JWT")
    @ApiResponse(responseCode = "201", description = "Аккаунт создан")
    @ApiResponse(responseCode = "409", description = "Email уже занят")
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Войти", description = "Проверяет email и пароль, возвращает JWT")
    @ApiResponse(responseCode = "401", description = "Неверные учётные данные")
    @ApiResponse(responseCode = "403", description = "Аккаунт заблокирован")
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Текущий пользователь")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.currentUser(principal);
    }

    @Operation(summary = "Удалить свой аккаунт", description = "Требует текущий пароль. Удаляются анкета, фото, реакции и матчи")
    @ApiResponse(responseCode = "204", description = "Аккаунт удалён")
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal AuthenticatedUser principal,
                         @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(principal, request);
    }
}
