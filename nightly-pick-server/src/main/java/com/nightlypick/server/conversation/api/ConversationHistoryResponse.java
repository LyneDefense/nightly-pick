package com.nightlypick.server.conversation.api;

import java.util.List;

public record ConversationHistoryResponse(
        List<ConversationHistoryGroupResponse> groups,
        int frozenCount
) {
}
