package com.nightlypick.server.audio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audio")
public record AudioProperties(
        String storageDir,
        String publicBaseUrl
) {
}
