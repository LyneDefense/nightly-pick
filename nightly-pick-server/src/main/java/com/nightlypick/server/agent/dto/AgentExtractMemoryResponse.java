package com.nightlypick.server.agent.dto;

import java.util.List;

public record AgentExtractMemoryResponse(
        List<AgentMemoryItemResponse> shortTermMemory
) {
}
