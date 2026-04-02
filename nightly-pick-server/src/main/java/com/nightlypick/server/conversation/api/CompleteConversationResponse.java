package com.nightlypick.server.conversation.api;

public record CompleteConversationResponse(
        String sessionId,
        String recordId,
        String title,
        boolean merged,
        String notice
) {
}
