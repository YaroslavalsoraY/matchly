package com.matchly.profile;

import java.time.LocalDate;

/**
 * Данные анкеты в терминах предметной области (без веб-аннотаций).
 * Через этот объект сущность {@link Profile} заполняется и при создании, и при редактировании.
 */
public record ProfileDetails(String displayName,
                             LocalDate birthDate,
                             Gender gender,
                             Preference lookingFor,
                             int ageMin,
                             int ageMax,
                             String city,
                             String bio,
                             String contact,
                             boolean visible) {
}
