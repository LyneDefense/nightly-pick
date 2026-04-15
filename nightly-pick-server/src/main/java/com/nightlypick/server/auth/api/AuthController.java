package com.nightlypick.server.auth.api;

import com.nightlypick.server.auth.application.AccessTokenStore;
import com.nightlypick.server.auth.application.WechatMiniappClient;
import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.UserProfileStore;
import com.nightlypick.server.user.domain.UserProfile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

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
        WechatMiniappClient.WechatLoginIdentity identity = wechatMiniappClient.resolveIdentity(
                request.loginCode(),
                request.phoneCode()
        );
        UserProfile user = userProfileStore.loginByPhone(
                identity.phone(),
                identity.openid(),
                "wechat_miniapp_phone",
                request.avatarUrl()
        );
        return ApiResponse.ok(new LoginResponse(accessTokenStore.createToken(user.id()), user));
    }
}
