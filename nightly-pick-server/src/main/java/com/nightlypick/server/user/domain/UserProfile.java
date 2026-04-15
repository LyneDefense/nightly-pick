package com.nightlypick.server.user.domain;

public record UserProfile(
        String id,
        String nickname,
        String phone,
        String avatarUrl,
        boolean allowMemoryReference,
        String createdDate,
        int businessDayResetHour
) {
}
