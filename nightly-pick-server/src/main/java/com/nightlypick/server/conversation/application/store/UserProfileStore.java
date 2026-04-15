package com.nightlypick.server.conversation.application.store;

import com.nightlypick.server.common.time.BusinessDayClock;
import com.nightlypick.server.persistence.entity.UserEntity;
import com.nightlypick.server.persistence.mapper.UserMapper;
import com.nightlypick.server.user.domain.UserProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class UserProfileStore {
    private final BusinessDayClock businessDayClock;
    private final UserMapper userMapper;

    public UserProfileStore(BusinessDayClock businessDayClock, UserMapper userMapper) {
        this.businessDayClock = businessDayClock;
        this.userMapper = userMapper;
    }

    public UserProfile loginDevUser(String nickname) {
        UserEntity existing = userMapper.selectById("demo-user");
        if (existing == null) {
            UserEntity entity = new UserEntity();
            entity.setId("demo-user");
            entity.setNickname(nickname == null || nickname.isBlank() ? "夜拾用户" : nickname);
            entity.setProvider("dev");
            entity.setAllowMemoryReference(true);
            entity.setCreatedDate(LocalDate.now());
            entity.setUpdatedAt(OffsetDateTime.now());
            entity.setLastLoginAt(entity.getUpdatedAt());
            userMapper.insert(entity);
            return toDomain(entity);
        }
        markLogin(existing, null, null, null);
        return toDomain(existing);
    }

    public UserProfile loginByPhone(String phone, String openid, String provider, String avatarUrl) {
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少手机号");
        }
        UserEntity existing = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getPhone, phone)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            UserEntity entity = new UserEntity();
            entity.setId("user-" + UUID.randomUUID());
            entity.setNickname("夜拾用户");
            entity.setPhone(phone);
            entity.setOpenid(blankToNull(openid));
            entity.setProvider(provider == null || provider.isBlank() ? "wechat_miniapp_phone" : provider);
            entity.setAvatarUrl(blankToNull(avatarUrl));
            entity.setAllowMemoryReference(true);
            entity.setCreatedDate(LocalDate.now());
            entity.setUpdatedAt(OffsetDateTime.now());
            entity.setLastLoginAt(entity.getUpdatedAt());
            userMapper.insert(entity);
            return toDomain(entity);
        }
        markLogin(existing, openid, provider, avatarUrl);
        return toDomain(userMapper.selectById(existing.getId()));
    }

    public UserProfile getUser(String userId) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return toDomain(entity);
    }

    public UserProfile updateUserSettings(String userId, boolean allowMemoryReference) {
        UserProfile current = getUser(userId);
        UserEntity entity = new UserEntity();
        entity.setId(current.id());
        entity.setNickname(current.nickname());
        entity.setAllowMemoryReference(allowMemoryReference);
        entity.setCreatedDate(LocalDate.parse(current.createdDate()));
        entity.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(entity);
        return getUser(current.id());
    }

    private void markLogin(UserEntity existing, String openid, String provider, String avatarUrl) {
        UserEntity entity = new UserEntity();
        entity.setId(existing.getId());
        entity.setNickname(existing.getNickname());
        entity.setPhone(existing.getPhone());
        entity.setOpenid(blankToNull(openid) == null ? existing.getOpenid() : openid);
        entity.setProvider(provider == null || provider.isBlank() ? existing.getProvider() : provider);
        entity.setAvatarUrl(blankToNull(avatarUrl) == null ? existing.getAvatarUrl() : avatarUrl);
        entity.setAllowMemoryReference(existing.getAllowMemoryReference());
        entity.setCreatedDate(existing.getCreatedDate());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setLastLoginAt(entity.getUpdatedAt());
        userMapper.updateById(entity);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private UserProfile toDomain(UserEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getNickname(),
                maskPhone(entity.getPhone()),
                entity.getAvatarUrl(),
                Boolean.TRUE.equals(entity.getAllowMemoryReference()),
                entity.getCreatedDate().toString(),
                businessDayClock.resetHour()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
