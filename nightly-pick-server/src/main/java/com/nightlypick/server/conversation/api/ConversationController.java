package com.nightlypick.server.conversation.api;

import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.ConversationFlowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationFlowService conversationFlowService;

    public ConversationController(ConversationFlowService conversationFlowService) {
        this.conversationFlowService = conversationFlowService;
    }

    @PostMapping
    public ApiResponse<CreateConversationResponse> createConversation() {
        return ApiResponse.ok(conversationFlowService.createConversation());
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResponse<SendMessageResponse> sendMessage(
            @PathVariable String sessionId,
            @RequestBody SendMessageRequest request
    ) {
        return ApiResponse.ok(conversationFlowService.sendMessage(sessionId, request));
    }

    @PostMapping("/{sessionId}/complete")
    public ApiResponse<CompleteConversationResponse> completeConversation(@PathVariable String sessionId) {
        return ApiResponse.ok(conversationFlowService.completeConversation(sessionId));
    }

    @PostMapping("/{sessionId}/autosave")
    public ApiResponse<AutosaveConversationResponse> autosaveConversation(@PathVariable String sessionId) {
        return ApiResponse.ok(conversationFlowService.autosaveConversation(sessionId));
    }

    @GetMapping("/active")
    public ApiResponse<Map<String, Object>> getActiveConversation() {
        return ApiResponse.ok(conversationFlowService.getActiveConversation());
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<Map<String, Object>> getConversation(@PathVariable String sessionId) {
        return ApiResponse.ok(conversationFlowService.getConversation(sessionId));
    }
}
