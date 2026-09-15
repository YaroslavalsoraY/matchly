package com.matchly.profile;

import java.time.Instant;

/** Сведения о фото без самих байтов: достаточно, чтобы построить ссылку с версией для кэша браузера. */
public record PhotoMeta(Long profileId, Instant updatedAt) {

    public String url() {
        return "/api/profiles/%d/photo?v=%d".formatted(profileId, updatedAt.toEpochMilli());
    }
}
