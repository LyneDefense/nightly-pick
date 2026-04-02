package com.nightlypick.server.conversation.api;

public record CreateConversationResponse(
        String sessionId,
        String status
) {
}
