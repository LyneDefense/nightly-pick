package com.nightlypick.server.auth.application;

import com.nightlypick.server.persistence.entity.AccessTokenEntity;
import com.nightlypick.server.persistence.mapper.AccessTokenMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Component
public class AccessTokenStore {
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_DAYS = 30;

    private final AccessTokenMapper accessTokenMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccessTokenStore(AccessTokenMapper accessTokenMapper) {
        this.accessTokenMapper = accessTokenMapper;
    }

    public String createToken(String userId) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        AccessTokenEntity entity = new AccessTokenEntity();
        entity.setToken(token);
        entity.setUserId(userId);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setExpiresAt(entity.getCreatedAt().plusDays(TOKEN_DAYS));
        accessTokenMapper.insert(entity);
        return token;
    }

    public String requireUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        AccessTokenEntity entity = accessTokenMapper.selectById(token);
        if (entity == null || entity.getRevokedAt() != null || entity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return entity.getUserId();
    }
}
