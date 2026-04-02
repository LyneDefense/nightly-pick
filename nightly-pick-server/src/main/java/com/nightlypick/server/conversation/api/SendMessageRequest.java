package com.nightlypick.server.conversation.api;

public record SendMessageRequest(
        String text,
        String inputType
) {
}
