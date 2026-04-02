package com.nightlypick.server.audio.api;

public record AudioTranscribeRequest(
        String sessionId,
        String audioUrl
) {
}
