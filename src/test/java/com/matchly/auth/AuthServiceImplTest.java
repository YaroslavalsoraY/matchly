package com.matchly.auth;

import com.matchly.auth.dto.AuthResponse;
import com.matchly.auth.dto.LoginRequest;
import com.matchly.auth.dto.RegisterRequest;
import com.matchly.common.exception.AccountBlockedException;
import com.matchly.common.exception.ConflictException;
import com.matchly.common.exception.InvalidCredentialsException;
import com.matchly.security.JwtTokenService;
import com.matchly.user.Role;
import com.matchly.user.User;
import com.matchly.user.UserMapper;
import com.matchly.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit-тесты бизнес-логики входа и регистрации: зависимости замоканы, база не нужна. */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final JwtTokenService.IssuedToken TOKEN =
            new JwtTokenService.IssuedToken("jwt-token", Instant.parse("2030-01-01T00:00:00Z"));

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService tokenService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, tokenService, new UserMapper());
    }

    @Test
    void register_normalizesEmail_hashesPassword_andIssuesToken() {
        when(userRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("{bcrypt}hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));
        when(tokenService.issue(any(User.class))).thenReturn(TOKEN);

        AuthResponse response = service.register(new RegisterRequest("  John@Example.COM ", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.expiresAt()).isEqualTo(TOKEN.expiresAt());
        assertThat(response.user().id()).isEqualTo(7L);
        assertThat(response.user().email()).isEqualTo("john@example.com");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().isBlocked()).isFalse();
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("dup@example.com", "secret123")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email is already registered");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = withId(User.register("jane@example.com", "hash"), 1L);
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenService, never()).issue(any());
    }

    @Test
    void login_blockedUser_throwsAccountBlocked() {
        User user = withId(User.register("blocked@example.com", "hash"), 2L);
        user.block();
        when(userRepository.findByEmailIgnoreCase("blocked@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("blocked@example.com", "secret")))
                .isInstanceOf(AccountBlockedException.class);
    }

    @Test
    void login_success_returnsTokenAndUser() {
        User user = withId(User.register("ok@example.com", "hash"), 3L);
        when(userRepository.findByEmailIgnoreCase("ok@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn(TOKEN);

        AuthResponse response = service.login(new LoginRequest("ok@example.com", "secret"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(3L);
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
