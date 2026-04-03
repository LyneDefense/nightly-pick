package com.nightlypick.server.conversation.domain;

import java.time.OffsetDateTime;

public record ConversationSession(
        String id,
        String userId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        int userMessageCount,
        int summarizedUserMessageCount,
        String summaryJobStatus,
        int summaryJobTargetUserMessageCount
) {
}
