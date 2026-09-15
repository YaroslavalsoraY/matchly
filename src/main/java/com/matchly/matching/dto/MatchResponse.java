package com.matchly.matching.dto;

import com.matchly.profile.dto.ProfileCardResponse;

import java.time.Instant;

/** Матч глазами одного из участников: карточка собеседника и его контакт. */
public record MatchResponse(Long id, ProfileCardResponse profile, String contact, Instant matchedAt) {
}
