package com.matchly.reaction;

import com.matchly.common.exception.BusinessRuleException;
import com.matchly.common.exception.NotFoundException;
import com.matchly.matching.Match;
import com.matchly.matching.MatchAssembler;
import com.matchly.matching.MatchRepository;
import com.matchly.matching.dto.MatchResponse;
import com.matchly.profile.Profile;
import com.matchly.profile.ProfileRepository;
import com.matchly.reaction.dto.ReactionRequest;
import com.matchly.reaction.dto.ReactionResponse;
import com.matchly.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactionServiceImpl implements ReactionService {

    private final ProfileRepository profileRepository;
    private final ReactionRepository reactionRepository;
    private final MatchRepository matchRepository;
    private final MatchAssembler matchAssembler;

    @Override
    @Transactional
    public ReactionResponse react(Long userId, ReactionRequest request) {
        Profile me = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        if (me.getId().equals(request.targetProfileId())) {
            throw new BusinessRuleException("You cannot react to your own profile");
        }
        Profile target = profileRepository.findVisibleById(request.targetProfileId(), UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Profile", request.targetProfileId()));

        Reaction reaction = reactionRepository.findBySource_IdAndTarget_Id(me.getId(), target.getId())
                .map(existing -> {
                    existing.change(request.type());
                    return existing;
                })
                .orElseGet(() -> reactionRepository.save(new Reaction(me, target, request.type())));
        log.debug("Profile {} reacted {} to profile {}", me.getId(), reaction.getType(), target.getId());

        MatchResponse match = reaction.isLike() ? tryMatch(me, target) : breakMatch(me, target);
        return new ReactionResponse(target.getId(), reaction.getType(), match != null, match);
    }

    /** Если лайк взаимный, возвращает (создавая при необходимости) матч. */
    private MatchResponse tryMatch(Profile me, Profile target) {
        boolean mutual = reactionRepository.existsBySource_IdAndTarget_IdAndType(target.getId(), me.getId(), ReactionType.LIKE);
        if (!mutual) {
            return null;
        }
        Match match = findMatch(me, target).orElseGet(() -> {
            Match created = matchRepository.save(Match.between(me, target));
            log.info("New match between profiles {} and {}", me.getId(), target.getId());
            return created;
        });
        return matchAssembler.toResponse(match, me.getId());
    }

    /** Пропуск после матча разрывает его. */
    private MatchResponse breakMatch(Profile me, Profile target) {
        findMatch(me, target).ifPresent(match -> {
            matchRepository.delete(match);
            log.info("Match between profiles {} and {} removed after skip", me.getId(), target.getId());
        });
        return null;
    }

    private Optional<Match> findMatch(Profile first, Profile second) {
        long a = Math.min(first.getId(), second.getId());
        long b = Math.max(first.getId(), second.getId());
        return matchRepository.findByProfileA_IdAndProfileB_Id(a, b);
    }
}
