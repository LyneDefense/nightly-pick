package com.nightlypick.server.conversation.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ConversationHistoryItemResponse(
        String sessionId,
        String status,
        LocalDate businessDate,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        int messageCount,
        int userMessageCount,
        String preview
) {
}
