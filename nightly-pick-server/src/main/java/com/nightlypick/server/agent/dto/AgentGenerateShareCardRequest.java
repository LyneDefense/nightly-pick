package com.nightlypick.server.agent.dto;

import java.util.List;

public record AgentGenerateShareCardRequest(
        String recordId,
        String cardType,
        String recordDate,
        String title,
        String summary,
        String highlight,
        List<String> events,
        List<String> emotions,
        List<String> openLoops
) {
}
