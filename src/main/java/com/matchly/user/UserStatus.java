package com.matchly.user;

/** Состояние аккаунта. Заблокированный пользователь не может войти и не показывается в рекомендациях. */
public enum UserStatus {
    ACTIVE,
    BLOCKED
}
