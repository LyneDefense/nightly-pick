package com.nightlypick.server.audio.api;

public record AudioUploadRequest(
        String sessionId,
        String fileName
) {
}
