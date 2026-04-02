package com.nightlypick.server.conversation.application;

import com.nightlypick.server.conversation.application.service.ConversationReplyService;
import com.nightlypick.server.conversation.application.service.RecordGenerationService;
import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import com.nightlypick.server.conversation.api.AutosaveConversationResponse;
import com.nightlypick.server.conversation.api.CompleteConversationResponse;
import com.nightlypick.server.conversation.api.CreateConversationResponse;
import com.nightlypick.server.conversation.api.SendMessageRequest;
import com.nightlypick.server.conversation.api.SendMessageResponse;
import com.nightlypick.server.conversation.domain.ConversationSession;
import com.nightlypick.server.user.application.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ConversationFlowService {
    private static final Logger log = LoggerFactory.getLogger(ConversationFlowService.class);
    private final ConversationSessionStore conversationSessionStore;
    private final ConversationReplyService conversationReplyService;
    private final RecordGenerationService recordGenerationService;
    private final UserContext userContext;

    public ConversationFlowService(
            ConversationSessionStore conversationSessionStore,
            ConversationReplyService conversationReplyService,
            RecordGenerationService recordGenerationService,
            UserContext userContext
    ) {
        this.conversationSessionStore = conversationSessionStore;
        this.conversationReplyService = conversationReplyService;
        this.recordGenerationService = recordGenerationService;
        this.userContext = userContext;
    }

    public CreateConversationResponse createConversation() {
        log.info("开始创建或恢复对话会话 userId={}", userContext.getCurrentUserId());
        ConversationSession session = conversationSessionStore.findActiveSession(userContext.getCurrentUserId());
        if (session == null) {
            session = conversationSessionStore.createSession(userContext.getCurrentUserId());
            log.info("已创建新的对话会话 sessionId={} status={}", session.id(), session.status());
        } else {
            log.info("已复用当前活跃会话 sessionId={} status={}", session.id(), session.status());
        }
        return new CreateConversationResponse(session.id(), session.status());
    }

    public SendMessageResponse sendMessage(String sessionId, SendMessageRequest request) {
        return conversationReplyService.sendMessage(sessionId, request);
    }

    public CompleteConversationResponse completeConversation(String sessionId) {
        log.info("开始执行主动完成总结 sessionId={}", sessionId);
        RecordGenerationService.SavedRecordResult result = recordGenerationService.saveOrUpdateDailyRecord(sessionId, true);
        log.info("主动完成总结成功 sessionId={} recordId={} merged={}", sessionId, result.record().id(), result.merged());
        return new CompleteConversationResponse(
                sessionId,
                result.record().id(),
                result.record().title(),
                result.merged(),
                result.merged() ? "已合并到今日总结" : "已生成今日总结"
        );
    }

    public AutosaveConversationResponse autosaveConversation(String sessionId) {
        log.info("开始执行自动总结 sessionId={}", sessionId);
        RecordGenerationService.SavedRecordResult result = recordGenerationService.saveOrUpdateDailyRecord(sessionId, false);
        log.info("自动总结成功 sessionId={} recordId={} merged={}", sessionId, result.record().id(), result.merged());
        return new AutosaveConversationResponse(
                sessionId,
                result.record().id(),
                result.record().title(),
                result.merged()
        );
    }

    public Map<String, Object> getConversation(String sessionId) {
        log.info("查询会话详情 sessionId={}", sessionId);
        ConversationSession session = conversationSessionStore.getSession(sessionId);
        return Map.of(
                "session", session,
                "messages", conversationSessionStore.getMessages(sessionId)
        );
    }

    public Map<String, Object> getActiveConversation() {
        log.info("查询当前活跃会话 userId={}", userContext.getCurrentUserId());
        ConversationSession session = conversationSessionStore.findActiveSession(userContext.getCurrentUserId());
        if (session == null) {
            log.info("当前没有可恢复的活跃会话 userId={}", userContext.getCurrentUserId());
            return null;
        }
        log.info("查询到活跃会话 sessionId={} status={}", session.id(), session.status());
        return Map.of(
                "session", session,
                "messages", conversationSessionStore.getMessages(session.id())
        );
    }
}
