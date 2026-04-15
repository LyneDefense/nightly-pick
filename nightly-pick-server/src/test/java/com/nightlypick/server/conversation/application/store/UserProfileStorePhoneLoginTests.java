package com.nightlypick.server.conversation.application.store;

import com.nightlypick.server.persistence.entity.UserEntity;
import com.nightlypick.server.persistence.mapper.UserMapper;
import com.nightlypick.server.user.domain.UserProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class UserProfileStorePhoneLoginTests {

    @Autowired
    private UserProfileStore userProfileStore;

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldCreateUserWhenPhoneIsNewAndReuseSameUserWhenPhoneExists() {
        String phone = "138" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        UserProfile firstLogin = userProfileStore.loginOrCreateByPhone(
                phone,
                "openid-first",
                "wechat_miniapp_phone",
                "https://example.com/avatar-first.png"
        );

        Assertions.assertNotNull(firstLogin);
        Assertions.assertEquals(maskPhone(phone), firstLogin.phone());
        Assertions.assertEquals("https://example.com/avatar-first.png", firstLogin.avatarUrl());

        UserEntity firstEntity = userMapper.selectById(firstLogin.id());
        Assertions.assertNotNull(firstEntity);
        Assertions.assertEquals(phone, firstEntity.getPhone());
        Assertions.assertEquals("openid-first", firstEntity.getOpenid());
        Assertions.assertEquals("wechat_miniapp_phone", firstEntity.getProvider());
        Assertions.assertEquals("https://example.com/avatar-first.png", firstEntity.getAvatarUrl());

        UserProfile secondLogin = userProfileStore.loginOrCreateByPhone(
                phone,
                "openid-second",
                "wechat_miniapp_phone",
                "https://example.com/avatar-second.png"
        );

        Assertions.assertEquals(firstLogin.id(), secondLogin.id());
        Assertions.assertEquals(maskPhone(phone), secondLogin.phone());
        Assertions.assertEquals("https://example.com/avatar-second.png", secondLogin.avatarUrl());

        UserEntity secondEntity = userMapper.selectById(firstLogin.id());
        Assertions.assertNotNull(secondEntity);
        Assertions.assertEquals(phone, secondEntity.getPhone());
        Assertions.assertEquals("openid-second", secondEntity.getOpenid());
        Assertions.assertEquals("https://example.com/avatar-second.png", secondEntity.getAvatarUrl());
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
