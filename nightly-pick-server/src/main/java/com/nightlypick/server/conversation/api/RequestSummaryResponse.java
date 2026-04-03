package com.nightlypick.server.conversation.api;

public record RequestSummaryResponse(
        String sessionId,
        String notice,
        ConversationSummaryStatusResponse summaryStatus
) {
}
