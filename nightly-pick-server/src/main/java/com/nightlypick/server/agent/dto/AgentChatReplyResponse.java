package com.nightlypick.server.agent.dto;
public record AgentChatReplyResponse(
        String replyText,
        boolean shouldEnd,
        String stage
) {
}
