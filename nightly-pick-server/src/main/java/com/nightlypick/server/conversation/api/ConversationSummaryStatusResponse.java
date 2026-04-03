package com.nightlypick.server.conversation.api;

public record ConversationSummaryStatusResponse(
        String status,
        String recordId,
        int userMessageCount,
        int summarizedUserMessageCount
) {
}
