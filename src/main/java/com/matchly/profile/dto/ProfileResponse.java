package com.matchly.profile.dto;

import com.matchly.interest.dto.InterestResponse;
import com.matchly.profile.Gender;
import com.matchly.profile.Preference;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Полная анкета владельца, включая настройки поиска и контакт. */
public record ProfileResponse(Long id,
                              Long userId,
                              String displayName,
                              LocalDate birthDate,
                              int age,
                              Gender gender,
                              Preference lookingFor,
                              int ageMin,
                              int ageMax,
                              String city,
                              String bio,
                              String contact,
                              boolean visible,
                              List<InterestResponse> interests,
                              String photoUrl,
                              Instant updatedAt) {
}
