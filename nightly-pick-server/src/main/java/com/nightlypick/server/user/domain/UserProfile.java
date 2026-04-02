package com.nightlypick.server.user.domain;

public record UserProfile(
        String id,
        String nickname,
        boolean allowMemoryReference,
        String createdDate,
        int businessDayResetHour
) {
}
