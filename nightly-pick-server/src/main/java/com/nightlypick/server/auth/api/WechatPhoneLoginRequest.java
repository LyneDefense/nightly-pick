package com.nightlypick.server.auth.api;

public record WechatPhoneLoginRequest(
        String loginCode,
        String phoneCode,
        String encryptedData,
        String iv,
        String avatarUrl
) {
}
