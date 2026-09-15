package com.matchly.reaction;

import com.matchly.reaction.dto.ReactionRequest;
import com.matchly.reaction.dto.ReactionResponse;

/** Лайки и пропуски; создание и разрыв матчей как следствие реакций. */
public interface ReactionService {

    /**
     * Сохраняет реакцию (повторная реакция на ту же анкету меняет тип).
     * Взаимный лайк создаёт матч, пропуск после матча его удаляет.
     */
    ReactionResponse react(Long userId, ReactionRequest request);
}
