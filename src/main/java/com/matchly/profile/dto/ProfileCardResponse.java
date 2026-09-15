package com.matchly.profile.dto;

import com.matchly.interest.dto.InterestResponse;
import com.matchly.profile.Gender;

import java.util.List;

/** Карточка чужой анкеты: без контакта и настроек поиска. */
public record ProfileCardResponse(Long id,
                                  String displayName,
                                  int age,
                                  Gender gender,
                                  String city,
                                  String bio,
                                  List<InterestResponse> interests,
                                  String photoUrl) {
}
