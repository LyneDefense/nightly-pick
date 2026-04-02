package com.nightlypick.server.conversation.api;

import com.nightlypick.server.agent.dto.AgentChatReplyResponse;
import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.ConversationApplicationService;
import com.nightlypick.server.conversation.application.TestAgentRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class TestAgentController {

    private final ConversationApplicationService conversationApplicationService;

    public TestAgentController(ConversationApplicationService conversationApplicationService) {
        this.conversationApplicationService = conversationApplicationService;
    }

    @PostMapping("/test-agent")
    public ApiResponse<AgentChatReplyResponse> testAgent(@RequestBody TestAgentRequest request) {
        return ApiResponse.ok(conversationApplicationService.testAgentReply(request));
    }
}
