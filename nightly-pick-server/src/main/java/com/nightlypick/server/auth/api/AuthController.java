package com.nightlypick.server.auth.api;

import com.nightlypick.server.auth.application.AccessTokenStore;
import com.nightlypick.server.auth.application.WechatMiniappClient;
import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.UserProfileStore;
import com.nightlypick.server.user.domain.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserProfileStore userProfileStore;
    private final AccessTokenStore accessTokenStore;
    private final WechatMiniappClient wechatMiniappClient;

    public AuthController(
            UserProfileStore userProfileStore,
            AccessTokenStore accessTokenStore,
            WechatMiniappClient wechatMiniappClient
    ) {
        this.userProfileStore = userProfileStore;
        this.accessTokenStore = accessTokenStore;
        this.wechatMiniappClient = wechatMiniappClient;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody(required = false) LoginRequest request) {
        String nickname = request == null ? null : request.nickname();
        UserProfile user = userProfileStore.loginDevUser(nickname);
        return ApiResponse.ok(new LoginResponse(accessTokenStore.createToken(user.id()), user));
    }

    @PostMapping("/wechat-phone-login")
    public ApiResponse<LoginResponse> loginWithWechatPhone(@RequestBody WechatPhoneLoginRequest request) {
        log.info("微信手机号登录请求 received hasLoginCode={} hasPhoneCode={} hasEncryptedData={} hasIv={} hasAvatarUrl={}",
                request.loginCode() != null && !request.loginCode().isBlank(),
                request.phoneCode() != null && !request.phoneCode().isBlank(),
                request.encryptedData() != null && !request.encryptedData().isBlank(),
                request.iv() != null && !request.iv().isBlank(),
                request.avatarUrl() != null && !request.avatarUrl().isBlank());
        WechatMiniappClient.WechatLoginIdentity identity = wechatMiniappClient.resolveIdentity(
                request.loginCode(),
                request.phoneCode(),
                request.encryptedData(),
                request.iv()
        );
        log.info("微信手机号登录已解析 identity phoneSuffix={} hasOpenid={}",
                maskPhone(identity.phone()),
                identity.openid() != null && !identity.openid().isBlank());
        UserProfile user = userProfileStore.loginByPhone(
                identity.phone(),
                identity.openid(),
                "wechat_miniapp_phone",
                request.avatarUrl()
        );
        log.info("微信手机号登录完成 userId={} phoneSuffix={}", user.id(), maskPhone(identity.phone()));
        return ApiResponse.ok(new LoginResponse(accessTokenStore.createToken(user.id()), user));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }
        return "***" + phone.substring(Math.max(0, phone.length() - 4));
    }
}
