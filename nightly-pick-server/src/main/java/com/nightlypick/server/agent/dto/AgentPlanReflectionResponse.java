package com.nightlypick.server.agent.dto;

import java.util.List;

public record AgentPlanReflectionResponse(
        String reflectionDepth,
        String tone,
        boolean shouldListFacts,
        boolean shouldListUnfinished,
        boolean shouldMakeConclusion,
        List<String> focus,
        List<String> whatHappenedToday,
        List<String> wantedButNotDone,
        String coreTension,
        String recordShape
) {
}
