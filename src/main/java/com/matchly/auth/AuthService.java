package com.matchly.auth;

import com.matchly.auth.dto.AuthResponse;
import com.matchly.auth.dto.DeleteAccountRequest;
import com.matchly.auth.dto.LoginRequest;
import com.matchly.auth.dto.RegisterRequest;
import com.matchly.security.AuthenticatedUser;
import com.matchly.user.dto.UserResponse;

/** Регистрация, вход и сведения о текущем пользователе. */
public interface AuthService {

    /** Создаёт аккаунт с ролью USER и сразу выдаёт токен. */
    AuthResponse register(RegisterRequest request);

    /** Проверяет учётные данные и выдаёт токен. */
    AuthResponse login(LoginRequest request);

    /** Актуальные данные аккаунта, от имени которого выполнен запрос. */
    UserResponse currentUser(AuthenticatedUser principal);

    /** Безвозвратно удаляет аккаунт вместе с анкетой, фото, реакциями и матчами. Требует пароль. */
    void deleteAccount(AuthenticatedUser principal, DeleteAccountRequest request);
}
