package com.nightlypick.server.user.application;

import com.nightlypick.server.auth.application.AccessTokenStore;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class UserContext {
    private final AccessTokenStore accessTokenStore;

    public UserContext(AccessTokenStore accessTokenStore) {
        this.accessTokenStore = accessTokenStore;
    }

    public String getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String authorization = attributes == null ? null : attributes.getRequest().getHeader("Authorization");
        String token = extractBearerToken(authorization);
        return accessTokenStore.requireUserId(token);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) return null;
        return authorization.substring(prefix.length()).trim();
    }
}
