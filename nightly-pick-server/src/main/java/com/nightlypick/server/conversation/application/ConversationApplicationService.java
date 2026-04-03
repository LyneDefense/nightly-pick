package com.nightlypick.server.conversation.application;

import com.nightlypick.server.agent.dto.AgentChatReplyRequest;
import com.nightlypick.server.agent.dto.AgentChatReplyResponse;
import com.nightlypick.server.agent.service.AgentClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationApplicationService {

    private final AgentClient agentClient;

    public ConversationApplicationService(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    public AgentChatReplyResponse testAgentReply(TestAgentRequest request) {
        return agentClient.getChatReply(new AgentChatReplyRequest(
                request.sessionId(),
                request.userInput(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                List.of(),
                true
        ));
    }
}
