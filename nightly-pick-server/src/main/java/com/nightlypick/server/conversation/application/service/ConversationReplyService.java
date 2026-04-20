package com.nightlypick.server.conversation.application.service;

import com.nightlypick.server.agent.dto.AgentChatReplyRequest;
import com.nightlypick.server.agent.dto.AgentChatReplyResponse;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechRequest;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechResponse;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.common.timing.TimingLogService;
import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import com.nightlypick.server.conversation.application.ConversationTextSanitizer;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final TimingLogService timingLogService;

    public ConversationReplyService(
            ConversationSessionStore conversationSessionStore,
            DailyRecordStore dailyRecordStore,
            UserProfileStore userProfileStore,
            MemoryStore memoryStore,
            AgentClient agentClient,
            UserContext userContext,
            BusinessDayClock businessDayClock,
            TimingLogService timingLogService
    ) {
        this.conversationSessionStore = conversationSessionStore;
        this.dailyRecordStore = dailyRecordStore;
        this.userProfileStore = userProfileStore;
        this.memoryStore = memoryStore;
        this.agentClient = agentClient;
        this.userContext = userContext;
        this.businessDayClock = businessDayClock;
        this.timingLogService = timingLogService;
    }

    public SendMessageResponse sendMessage(String sessionId, SendMessageRequest request) {
        long startedAt = System.nanoTime();
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        String status = "ok";
        String errorMessage = null;
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
        String assistantAudioUrl = null;
        boolean shouldEnd = false;
        String stage = "opening";
        String dominantMode = "companionship";
        String reflectionReadiness = "not_ready";
        String visibleReplyText = "";
        try {
            timingLogService.turnBegin(sessionId, inputType, request.text());
            long contextStartedAt = System.nanoTime();
            conversationSessionStore.addMessage(sessionId, "user", inputType, request.text());
            List<ConversationMessage> history = conversationSessionStore.getMessages(sessionId);
            UserProfile userProfile = userProfileStore.getUser(userId);
            boolean allowMemoryReference = userProfile.allowMemoryReference();
            String profileSummary = allowMemoryReference ? memoryStore.buildUserProfileSummary(userId) : null;
            String emotionalTrendSummary = allowMemoryReference ? memoryStore.buildEmotionalTrendSummary(userId) : null;
            String strategyHints = allowMemoryReference ? memoryStore.buildConversationStrategyHints(userId, request.text()) : null;
            List<String> relevantMemories = allowMemoryReference ? memoryStore.listRelevantMemoryContents(userId, request.text(), 3) : List.of();
            List<String> pendingUnansweredInputs = selectPendingUnansweredInputs(history);
            long contextElapsedMs = elapsedMs(contextStartedAt);
            steps.add(timingLogService.step("context_build", "整理上下文", contextElapsedMs));
            log.info("已整理对话上下文 sessionId={} historyCount={} allowMemoryReference={} relevantMemoryCount={}",
                    sessionId,
                    history.size(),
                    allowMemoryReference,
                    relevantMemories.size());

            long agentStartedAt = System.nanoTime();
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
            long agentElapsedMs = elapsedMs(agentStartedAt);
            steps.add(timingLogService.step("agent_reply", "请求 Agent 回复", agentElapsedMs));
            if (reply.replyText() == null || reply.replyText().isBlank()) {
                log.error("助手回复为空 sessionId={} reply={}", sessionId, reply);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent returned empty reply text.");
            }

            long sanitizeStartedAt = System.nanoTime();
            visibleReplyText = ConversationTextSanitizer.sanitizeAssistantText(reply.replyText());
            long sanitizeElapsedMs = elapsedMs(sanitizeStartedAt);
            steps.add(timingLogService.step("sanitize_reply", "净化回复内容", sanitizeElapsedMs));

            shouldEnd = reply.shouldEnd();
            stage = reply.stage();
            dominantMode = reply.dominantMode();
            reflectionReadiness = reply.reflectionReadiness();

            long persistStartedAt = System.nanoTime();
            conversationSessionStore.addMessage(sessionId, "assistant", "text", visibleReplyText);
            long persistElapsedMs = elapsedMs(persistStartedAt);
            steps.add(timingLogService.step("persist_reply", "写入回复", persistElapsedMs));

            if ("voice".equalsIgnoreCase(inputType)) {
                long ttsStartedAt = System.nanoTime();
                try {
                    AgentSynthesizeSpeechResponse speech = agentClient.synthesizeSpeech(new AgentSynthesizeSpeechRequest(visibleReplyText, null));
                    assistantAudioUrl = speech.audioUrl();
                    log.info("助手语音合成成功 sessionId={} hasAudioUrl={}", sessionId, assistantAudioUrl != null && !assistantAudioUrl.isBlank());
                } catch (RuntimeException error) {
                    log.warn("助手语音合成失败 sessionId={}", sessionId, error);
                } finally {
                    long ttsElapsedMs = elapsedMs(ttsStartedAt);
                    steps.add(timingLogService.step("speech_synthesize", "语音合成", ttsElapsedMs));
                }
            }

            log.info("用户消息处理完成 sessionId={} elapsedMs={}", sessionId, elapsedMs(startedAt));
            var todayRecord = dailyRecordStore.findRecordByUserAndDate(userId, businessDayClock.currentBusinessDate());
            String summaryStatus = todayRecord == null ? "noRecordYet" : "recordNeedsRefresh";
            return new SendMessageResponse(
                    sessionId,
                    request.text(),
                    visibleReplyText,
                    assistantAudioUrl,
                    shouldEnd,
                    stage,
                    dominantMode,
                    reflectionReadiness,
                    new ConversationSummaryStatusResponse(summaryStatus, todayRecord == null ? null : todayRecord.id(), 0, 0)
            );
        } catch (RuntimeException error) {
            status = "error";
            errorMessage = error.getMessage();
            throw error;
        } finally {
            payload.put("inputType", inputType);
            payload.put("sessionId", sessionId);
            payload.put("userId", userId);
            payload.put("status", status);
            payload.put("errorMessage", errorMessage);
            payload.put("assistantHasAudio", assistantAudioUrl != null && !assistantAudioUrl.isBlank());
            timingLogService.log(
                    "turn_end",
                    elapsedMs(startedAt),
                    steps,
                    payload
            );
        }
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
        int fromIndex = Math.max(0, previousUnansweredMessages.size() - 10);
        return previousUnansweredMessages.subList(fromIndex, previousUnansweredMessages.size()).stream()
                .filter(message -> message.text() != null && !message.text().isBlank())
                .map(ConversationMessage::text)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .distinct()
                .toList();
    }

    private long elapsedMs(long startedAtNanos) {
        return Math.max(0L, Math.round((System.nanoTime() - startedAtNanos) / 1_000_000.0));
    }
}
