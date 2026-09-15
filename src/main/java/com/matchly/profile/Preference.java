package com.matchly.profile;

/** Кого пользователь хочет видеть в рекомендациях. */
public enum Preference {
    MALE,
    FEMALE,
    EVERYONE;

    /** Подходит ли анкета с указанным полом под это предпочтение. */
    public boolean accepts(Gender gender) {
        return switch (this) {
            case EVERYONE -> true;
            case MALE -> gender == Gender.MALE;
            case FEMALE -> gender == Gender.FEMALE;
        };
    }
}
