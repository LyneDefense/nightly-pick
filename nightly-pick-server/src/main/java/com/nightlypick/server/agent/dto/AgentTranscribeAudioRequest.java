package com.nightlypick.server.agent.dto;
public record AgentTranscribeAudioRequest(
        String sessionId,
        String audioUrl
) {
}
