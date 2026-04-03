package com.nightlypick.server.agent.dto;

import java.util.List;

public record AgentChatReplyRequest(
        String sessionId,
        String userInput,
        List<String> history,
        List<String> pendingUnansweredInputs,
        String profileSummary,
        String emotionalTrendSummary,
        String strategyHints,
        List<String> recentMemories,
        boolean allowMemoryReference
) {
}
