package com.nightlypick.server.conversation.application.store;

import com.nightlypick.server.common.time.BusinessDayClock;
import com.nightlypick.server.persistence.entity.UserEntity;
import com.nightlypick.server.persistence.mapper.UserMapper;
import com.nightlypick.server.user.application.UserContext;
import com.nightlypick.server.user.domain.UserProfile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class UserProfileStore {
    private final BusinessDayClock businessDayClock;
    private final UserMapper userMapper;
    private final UserContext userContext;

    public UserProfileStore(BusinessDayClock businessDayClock, UserMapper userMapper, UserContext userContext) {
        this.businessDayClock = businessDayClock;
        this.userMapper = userMapper;
        this.userContext = userContext;
    }

    public UserProfile login(String nickname) {
        String userId = userContext.getCurrentUserId();
        UserEntity existing = userMapper.selectById(userId);
        if (existing == null) {
            UserEntity entity = new UserEntity();
            entity.setId(userId);
            entity.setNickname(nickname == null || nickname.isBlank() ? "夜拾用户" : nickname);
            entity.setAllowMemoryReference(true);
            entity.setCreatedDate(LocalDate.now());
            userMapper.insert(entity);
            return toDomain(entity);
        }
        return toDomain(existing);
    }

    public UserProfile getUser(String userId) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            return login("夜拾用户");
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
        userMapper.updateById(entity);
        return toDomain(entity);
    }

    private UserProfile toDomain(UserEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getNickname(),
                Boolean.TRUE.equals(entity.getAllowMemoryReference()),
                entity.getCreatedDate().toString(),
                businessDayClock.resetHour()
        );
    }
}
