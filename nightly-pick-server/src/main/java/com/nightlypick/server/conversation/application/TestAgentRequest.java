package com.nightlypick.server.conversation.application;

public record TestAgentRequest(
        String sessionId,
        String userInput
) {
}
