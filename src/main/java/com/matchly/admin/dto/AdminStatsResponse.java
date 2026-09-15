package com.matchly.admin.dto;

import com.matchly.interest.InterestUsage;

import java.util.List;

/** Сводные показатели сервиса для панели администратора. */
public record AdminStatsResponse(long users,
                                 long activeUsers,
                                 long blockedUsers,
                                 long newUsersLast7Days,
                                 long profiles,
                                 long visibleProfiles,
                                 long profilesWithPhoto,
                                 long likes,
                                 long skips,
                                 long matches,
                                 long interests,
                                 List<InterestUsage> topInterests) {
}
