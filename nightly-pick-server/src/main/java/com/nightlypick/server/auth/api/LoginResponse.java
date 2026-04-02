package com.nightlypick.server.auth.api;

import com.nightlypick.server.user.domain.UserProfile;

public record LoginResponse(
        String token,
        UserProfile user
) {
}
