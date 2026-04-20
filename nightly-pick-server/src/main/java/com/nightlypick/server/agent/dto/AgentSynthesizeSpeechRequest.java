package com.nightlypick.server.agent.dto;
public record AgentSynthesizeSpeechRequest(
        String text,
        String voiceId,
        String sessionId
) {
}
