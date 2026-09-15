package com.matchly.user;

import com.matchly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Учётная запись. Инкапсулирует правила смены состояния: снаружи нельзя произвольно
 * менять поля, доступны только осмысленные операции (заблокировать, разблокировать, сменить пароль).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    private User(String email, String passwordHash, Role role) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    /** Обычный пользователь, зарегистрировавшийся через форму. */
    public static User register(String email, String passwordHash) {
        return new User(email, passwordHash, Role.USER);
    }

    /** Администратор, создаваемый при первом запуске. */
    public static User createAdmin(String email, String passwordHash) {
        return new User(email, passwordHash, Role.ADMIN);
    }

    /** Email хранится в нижнем регистре без пробелов по краям, чтобы поиск был однозначным. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public void block() {
        this.status = UserStatus.BLOCKED;
    }

    public void unblock() {
        this.status = UserStatus.ACTIVE;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }
}
