package com.nightlypick.server.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat.miniapp")
public class WechatMiniappProperties {
    private String appId = "";
    private String secret = "";

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
