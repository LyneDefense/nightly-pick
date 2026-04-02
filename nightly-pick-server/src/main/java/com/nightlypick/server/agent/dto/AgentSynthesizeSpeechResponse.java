package com.nightlypick.server.agent.dto;
public record AgentSynthesizeSpeechResponse(
        String audioUrl,
        String voiceId
) {
}
