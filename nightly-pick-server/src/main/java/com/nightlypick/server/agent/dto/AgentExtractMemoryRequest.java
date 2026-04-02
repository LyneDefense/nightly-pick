package com.nightlypick.server.agent.dto;
import java.util.List;
public record AgentExtractMemoryRequest(
        String recordId,
        String summary,
        String conversationText,
        List<String> existingMemories
) {
}
