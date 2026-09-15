package com.matchly.matching;

import com.matchly.matching.dto.MatchResponse;

import java.util.List;

/** Список матчей пользователя и разрыв матча. */
public interface MatchService {

    List<MatchResponse> listMatches(Long userId);

    /** Удаляет матч и переводит собственный лайк в пропуск, чтобы анкета не вернулась в ленту. */
    void unmatch(Long userId, Long matchId);
}
