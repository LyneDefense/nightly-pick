package com.nightlypick.server.conversation.domain;

import java.time.OffsetDateTime;

public record ConversationMessage(
        String id,
        String sessionId,
        String role,
        String inputType,
        String text,
        OffsetDateTime createdAt
) {
}
