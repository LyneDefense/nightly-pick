package com.nightlypick.server.agent.dto;
public record AgentMemoryItemResponse(
        String type,
        String content,
        String sourceRecordId
) {
}
