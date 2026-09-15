package com.matchly.reaction;

/** Сколько лайков получила анкета (для рекомендаций по популярности и статистики). */
public record LikeCount(Long profileId, long likes) {
}
