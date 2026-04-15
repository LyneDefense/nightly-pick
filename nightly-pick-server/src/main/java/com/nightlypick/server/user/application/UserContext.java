package com.nightlypick.server.user.application;

import com.nightlypick.server.auth.application.AccessTokenStore;
import com.nightlypick.server.conversation.application.store.UserProfileStore;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserContext {
    private static final String DEMO_USER_ID = "demo-user";
    private final AccessTokenStore accessTokenStore;
    private final UserProfileStore userProfileStore;

    public UserContext(AccessTokenStore accessTokenStore, UserProfileStore userProfileStore) {
        this.accessTokenStore = accessTokenStore;
        this.userProfileStore = userProfileStore;
    }

    public String getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String authorization = attributes == null ? null : attributes.getRequest().getHeader("Authorization");
        String token = extractBearerToken(authorization);
        if (token != null && !token.isBlank()) {
            try {
                return accessTokenStore.requireUserId(token);
            } catch (ResponseStatusException ignored) {
                // Fall back to the demo user when phone login is unavailable or the token is absent/expired.
            }
        }
        ensureDemoUserExists();
        return DEMO_USER_ID;
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) return null;
        return authorization.substring(prefix.length()).trim();
    }

    private void ensureDemoUserExists() {
        userProfileStore.loginDevUser("夜拾用户");
    }
}
