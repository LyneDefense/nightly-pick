package com.nightlypick.server.conversation.api;

import java.util.List;

public record ConversationHistoryGroupResponse(
        String key,
        String title,
        List<ConversationHistoryItemResponse> items
) {
}
