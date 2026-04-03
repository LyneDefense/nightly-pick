package com.nightlypick.server.conversation.api;

public record SendMessageResponse(
        String sessionId,
        String userMessage,
        String assistantReply,
        String assistantAudioUrl,
        boolean shouldEnd,
        String stage,
        String dominantMode,
        String reflectionReadiness,
        ConversationSummaryStatusResponse summaryStatus
) {
}
