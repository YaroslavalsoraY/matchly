package com.matchly.reaction.dto;

import com.matchly.reaction.ReactionType;
import jakarta.validation.constraints.NotNull;

/** Реакция на анкету. */
public record ReactionRequest(@NotNull Long targetProfileId, @NotNull ReactionType type) {
}
