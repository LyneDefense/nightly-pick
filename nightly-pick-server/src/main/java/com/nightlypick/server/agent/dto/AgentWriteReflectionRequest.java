package com.nightlypick.server.agent.dto;

import java.util.List;

public record AgentWriteReflectionRequest(
        String sessionId,
        String conversationText,
        String existingTitle,
        String existingSummary,
        String existingHighlight,
        List<String> existingEvents,
        List<String> existingEmotions,
        List<String> existingOpenLoops,
        AgentPlanReflectionResponse plan
) {
}
