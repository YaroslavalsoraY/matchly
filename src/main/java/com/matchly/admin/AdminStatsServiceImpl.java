package com.matchly.admin;

import com.matchly.admin.dto.AdminStatsResponse;
import com.matchly.interest.InterestRepository;
import com.matchly.matching.MatchRepository;
import com.matchly.profile.ProfilePhotoRepository;
import com.matchly.profile.ProfileRepository;
import com.matchly.reaction.ReactionRepository;
import com.matchly.reaction.ReactionType;
import com.matchly.user.UserRepository;
import com.matchly.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final int TOP_INTERESTS = 5;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ProfilePhotoRepository photoRepository;
    private final ReactionRepository reactionRepository;
    private final MatchRepository matchRepository;
    private final InterestRepository interestRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse collect() {
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.BLOCKED),
                userRepository.countByCreatedAtAfter(weekAgo),
                profileRepository.count(),
                profileRepository.countByVisibleTrue(),
                photoRepository.count(),
                reactionRepository.countByType(ReactionType.LIKE),
                reactionRepository.countByType(ReactionType.SKIP),
                matchRepository.count(),
                interestRepository.count(),
                profileRepository.countProfilesPerInterest(PageRequest.of(0, TOP_INTERESTS)));
    }
}
