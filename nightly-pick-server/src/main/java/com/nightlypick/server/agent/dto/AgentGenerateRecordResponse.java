package com.nightlypick.server.agent.dto;

import java.util.List;

public record AgentGenerateRecordResponse(
        String title,
        String summary,
        List<String> events,
        List<String> emotions,
        List<String> openLoops,
        String highlight
) {
}
