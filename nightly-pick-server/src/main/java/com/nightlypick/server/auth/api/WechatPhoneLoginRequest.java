package com.nightlypick.server.auth.api;

public record WechatPhoneLoginRequest(
        String loginCode,
        String phoneCode,
        String avatarUrl
) {
}
