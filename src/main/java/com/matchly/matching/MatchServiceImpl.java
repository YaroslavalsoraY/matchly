package com.matchly.matching;

import com.matchly.common.exception.NotFoundException;
import com.matchly.matching.dto.MatchResponse;
import com.matchly.profile.Profile;
import com.matchly.profile.ProfileRepository;
import com.matchly.reaction.ReactionRepository;
import com.matchly.reaction.ReactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final ProfileRepository profileRepository;
    private final MatchRepository matchRepository;
    private final ReactionRepository reactionRepository;
    private final MatchAssembler matchAssembler;

    @Override
    @Transactional(readOnly = true)
    public List<MatchResponse> listMatches(Long userId) {
        Profile me = requireProfile(userId);
        return matchAssembler.toResponses(matchRepository.findAllForProfile(me.getId()), me.getId());
    }

    @Override
    @Transactional
    public void unmatch(Long userId, Long matchId) {
        Profile me = requireProfile(userId);
        Match match = matchRepository.findWithProfilesById(matchId)
                .filter(m -> m.involves(me.getId()))
                .orElseThrow(() -> new NotFoundException("Match", matchId));
        Profile other = match.other(me.getId());
        matchRepository.delete(match);
        reactionRepository.findBySource_IdAndTarget_Id(me.getId(), other.getId())
                .ifPresent(reaction -> reaction.change(ReactionType.SKIP));
        log.info("Profile {} unmatched profile {} (match id={})", me.getId(), other.getId(), matchId);
    }

    private Profile requireProfile(Long userId) {
        return profileRepository.findByUserId(userId).orElseThrow(() -> new NotFoundException("Profile not found"));
    }
}
