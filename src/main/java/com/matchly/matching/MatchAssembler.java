package com.matchly.matching;

import com.matchly.matching.dto.MatchResponse;
import com.matchly.profile.Profile;
import com.matchly.profile.ProfileCardAssembler;
import com.matchly.profile.dto.ProfileCardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Собирает ответ о матче для конкретного зрителя: показывает «другую сторону» и её контакт. */
@Component
@RequiredArgsConstructor
public class MatchAssembler {

    private final ProfileCardAssembler cardAssembler;

    public MatchResponse toResponse(Match match, Long viewerProfileId) {
        Profile other = match.other(viewerProfileId);
        return new MatchResponse(match.getId(), cardAssembler.toCard(other), other.getContact(), match.getCreatedAt());
    }

    /** Пакетная сборка: фото всех собеседников запрашиваются одним запросом. */
    public List<MatchResponse> toResponses(List<Match> matches, Long viewerProfileId) {
        List<Profile> others = matches.stream().map(m -> m.other(viewerProfileId)).toList();
        List<ProfileCardResponse> cards = cardAssembler.toCards(others);
        List<MatchResponse> result = new ArrayList<>(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            result.add(new MatchResponse(match.getId(), cards.get(i), others.get(i).getContact(), match.getCreatedAt()));
        }
        return result;
    }
}
