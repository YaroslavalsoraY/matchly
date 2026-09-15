package com.matchly.profile.dto;

import com.matchly.profile.Gender;
import com.matchly.profile.Preference;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

/** Создание или редактирование собственной анкеты. */
public record ProfileRequest(
        @NotBlank @Size(min = 2, max = 50) String displayName,
        @NotNull @Past LocalDate birthDate,
        @NotNull Gender gender,
        @NotNull Preference lookingFor,
        @Min(18) @Max(99) int ageMin,
        @Min(18) @Max(99) int ageMax,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 1000) String bio,
        @NotBlank @Size(max = 100) String contact,
        @NotEmpty @Size(max = 10) Set<@NotNull Long> interestIds,
        Boolean visible) {

    @AssertTrue(message = "ageMin must not exceed ageMax")
    public boolean isAgeRangeValid() {
        return ageMin <= ageMax;
    }

    /** Видимость по умолчанию включена. */
    public boolean visibleOrDefault() {
        return visible == null || visible;
    }
}
