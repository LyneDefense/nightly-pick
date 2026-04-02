package com.nightlypick.server.conversation.api;

public record AutosaveConversationResponse(
        String sessionId,
        String recordId,
        String title,
        boolean merged
) {
}
