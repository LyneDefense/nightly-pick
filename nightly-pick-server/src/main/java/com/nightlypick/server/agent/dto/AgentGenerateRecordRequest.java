package com.nightlypick.server.agent.dto;

public record AgentGenerateRecordRequest(
        String sessionId,
        String conversationText,
        String existingTitle,
        String existingSummary,
        String existingHighlight,
        java.util.List<String> existingEvents,
        java.util.List<String> existingEmotions,
        java.util.List<String> existingOpenLoops
) {
}
