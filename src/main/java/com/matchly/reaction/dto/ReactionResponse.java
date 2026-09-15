package com.matchly.reaction.dto;

import com.matchly.matching.dto.MatchResponse;
import com.matchly.reaction.ReactionType;

/** Результат реакции: если лайк оказался взаимным, сразу возвращается матч с контактом. */
public record ReactionResponse(Long targetProfileId, ReactionType type, boolean matched, MatchResponse match) {
}
