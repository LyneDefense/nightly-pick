package com.nightlypick.server.conversation.application.service;

import com.nightlypick.server.agent.dto.AgentChatReplyRequest;
import com.nightlypick.server.agent.dto.AgentChatReplyResponse;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechRequest;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechResponse;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import com.nightlypick.server.conversation.api.ConversationSummaryStatusResponse;
import com.nightlypick.server.conversation.application.store.MemoryStore;
import com.nightlypick.server.conversation.application.store.DailyRecordStore;
import com.nightlypick.server.conversation.application.store.UserProfileStore;
import com.nightlypick.server.conversation.api.SendMessageRequest;
import com.nightlypick.server.conversation.api.SendMessageResponse;
import com.nightlypick.server.conversation.domain.ConversationMessage;
import com.nightlypick.server.common.time.BusinessDayClock;
import com.nightlypick.server.user.application.UserContext;
import com.nightlypick.server.user.domain.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class ConversationReplyService {
    private static final Logger log = LoggerFactory.getLogger(ConversationReplyService.class);

    private final ConversationSessionStore conversationSessionStore;
    private final DailyRecordStore dailyRecordStore;
    private final UserProfileStore userProfileStore;
    private final MemoryStore memoryStore;
    private final AgentClient agentClient;
    private final UserContext userContext;
    private final BusinessDayClock businessDayClock;

    public ConversationReplyService(ConversationSessionStore conversationSessionStore, DailyRecordStore dailyRecordStore, UserProfileStore userProfileStore, MemoryStore memoryStore, AgentClient agentClient, UserContext userContext, BusinessDayClock businessDayClock) {
        this.conversationSessionStore = conversationSessionStore;
        this.dailyRecordStore = dailyRecordStore;
        this.userProfileStore = userProfileStore;
        this.memoryStore = memoryStore;
        this.agentClient = agentClient;
        this.userContext = userContext;
        this.businessDayClock = businessDayClock;
    }

    public SendMessageResponse sendMessage(String sessionId, SendMessageRequest request) {
        long startedAt = System.currentTimeMillis();
        log.info("开始处理用户消息 sessionId={} inputType={} textLength={} preview={}",
                sessionId,
                request.inputType(),
                request.text() == null ? 0 : request.text().length(),
                request.text() == null ? "" : request.text().substring(0, Math.min(request.text().length(), 80)));
        String inputType = request.inputType() == null || request.inputType().isBlank() ? "text" : request.inputType();
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message text is required.");
        }
        String userId = userContext.getCurrentUserId();
        conversationSessionStore.addMessage(sessionId, "user", inputType, request.text());
        List<ConversationMessage> history = conversationSessionStore.getMessages(sessionId);
        UserProfile userProfile = userProfileStore.getUser(userId);
        boolean allowMemoryReference = userProfile.allowMemoryReference();
        String profileSummary = allowMemoryReference ? memoryStore.buildUserProfileSummary(userId) : null;
        String emotionalTrendSummary = allowMemoryReference ? memoryStore.buildEmotionalTrendSummary(userId) : null;
        String strategyHints = allowMemoryReference ? memoryStore.buildConversationStrategyHints(userId, request.text()) : null;
        List<String> relevantMemories = allowMemoryReference ? memoryStore.listRelevantMemoryContents(userId, request.text(), 3) : List.of();
        List<String> pendingUnansweredInputs = selectPendingUnansweredInputs(history);
        log.info("已整理对话上下文 sessionId={} historyCount={} allowMemoryReference={} relevantMemoryCount={}",
                sessionId,
                history.size(),
                allowMemoryReference,
                relevantMemories.size());
        AgentChatReplyResponse reply = agentClient.getChatReply(new AgentChatReplyRequest(
                sessionId,
                request.text(),
                history.stream().map(message -> message.role() + ": " + message.text()).toList(),
                pendingUnansweredInputs,
                profileSummary,
                emotionalTrendSummary,
                strategyHints,
                relevantMemories,
                allowMemoryReference
        ));
        if (reply.replyText() == null || reply.replyText().isBlank()) {
            log.error("助手回复为空 sessionId={} reply={}", sessionId, reply);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent returned empty reply text.");
        }
        log.info("助手回复生成完成 sessionId={} stage={} shouldEnd={} replyLength={}",
                sessionId,
                reply.stage(),
                reply.shouldEnd(),
                reply.replyText().length());
        conversationSessionStore.addMessage(sessionId, "assistant", "text", reply.replyText());
        String assistantAudioUrl = null;
        if ("voice".equalsIgnoreCase(inputType)) {
            try {
                AgentSynthesizeSpeechResponse speech = agentClient.synthesizeSpeech(new AgentSynthesizeSpeechRequest(reply.replyText(), null));
                assistantAudioUrl = speech.audioUrl();
                log.info("助手语音合成成功 sessionId={} hasAudioUrl={}", sessionId, assistantAudioUrl != null && !assistantAudioUrl.isBlank());
            } catch (RuntimeException error) {
                log.warn("助手语音合成失败 sessionId={}", sessionId, error);
            }
        }
        log.info("用户消息处理完成 sessionId={} elapsedMs={}", sessionId, System.currentTimeMillis() - startedAt);
        var todayRecord = dailyRecordStore.findRecordByUserAndDate(userId, businessDayClock.currentBusinessDate());
        String summaryStatus = todayRecord == null ? "noRecordYet" : "recordNeedsRefresh";
        return new SendMessageResponse(
                sessionId,
                request.text(),
                reply.replyText(),
                assistantAudioUrl,
                reply.shouldEnd(),
                reply.stage(),
                reply.dominantMode(),
                reply.reflectionReadiness(),
                new ConversationSummaryStatusResponse(summaryStatus, todayRecord == null ? null : todayRecord.id(), 0, 0)
        );
    }

    private List<String> selectPendingUnansweredInputs(List<ConversationMessage> history) {
        if (history == null || history.isEmpty()) return List.of();
        int trailingAssistantIndex = -1;
        for (int index = history.size() - 1; index >= 0; index -= 1) {
            if ("assistant".equals(history.get(index).role())) {
                trailingAssistantIndex = index;
                break;
            }
        }
        List<ConversationMessage> unmatchedUserMessages = history.subList(trailingAssistantIndex + 1, history.size()).stream()
                .filter(message -> "user".equals(message.role()))
                .toList();
        if (unmatchedUserMessages.size() <= 1) {
            return List.of();
        }
        List<ConversationMessage> previousUnansweredMessages = unmatchedUserMessages.subList(0, unmatchedUserMessages.size() - 1);
        return previousUnansweredMessages.stream()
                .filter(message -> message.text() != null && !message.text().isBlank())
                .sorted(Comparator
                        .comparingInt((ConversationMessage message) -> scorePendingInput(message.text()))
                        .thenComparing(ConversationMessage::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .limit(2)
                .map(ConversationMessage::text)
                .toList();
    }

    private int scorePendingInput(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) return 0;
        int score = Math.min(normalized.length(), 80);
        if (normalized.length() >= 12) score += 20;
        if (normalized.contains("今天") || normalized.contains("刚刚") || normalized.contains("明天")) score += 15;
        if (normalized.contains("有点") || normalized.contains("难受") || normalized.contains("烦") || normalized.contains("想")) score += 15;
        if (normalized.length() <= 3) score -= 25;
        return score;
    }
}
