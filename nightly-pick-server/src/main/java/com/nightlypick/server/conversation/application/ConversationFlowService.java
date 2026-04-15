package com.nightlypick.server.conversation.application;

import com.nightlypick.server.conversation.application.service.ConversationReplyService;
import com.nightlypick.server.conversation.application.service.ConversationSummaryAsyncService;
import com.nightlypick.server.conversation.application.service.RecordGenerationService;
import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import com.nightlypick.server.common.time.BusinessDayClock;
import com.nightlypick.server.conversation.api.AutosaveConversationResponse;
import com.nightlypick.server.conversation.api.CompleteConversationResponse;
import com.nightlypick.server.conversation.api.ConversationHistoryGroupResponse;
import com.nightlypick.server.conversation.api.ConversationHistoryItemResponse;
import com.nightlypick.server.conversation.api.ConversationHistoryResponse;
import com.nightlypick.server.conversation.api.ConversationSummaryStatusResponse;
import com.nightlypick.server.conversation.api.CreateConversationResponse;
import com.nightlypick.server.conversation.api.RequestSummaryResponse;
import com.nightlypick.server.conversation.api.SendMessageRequest;
import com.nightlypick.server.conversation.api.SendMessageResponse;
import com.nightlypick.server.conversation.domain.ConversationMessage;
import com.nightlypick.server.conversation.domain.ConversationSession;
import com.nightlypick.server.conversation.domain.ConversationSummaryStatus;
import com.nightlypick.server.conversation.application.store.DailyRecordStore;
import com.nightlypick.server.record.domain.DailyRecord;
import com.nightlypick.server.user.application.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ConversationFlowService {
    private static final Logger log = LoggerFactory.getLogger(ConversationFlowService.class);
    private final ConversationSessionStore conversationSessionStore;
    private final ConversationReplyService conversationReplyService;
    private final RecordGenerationService recordGenerationService;
    private final ConversationSummaryAsyncService conversationSummaryAsyncService;
    private final DailyRecordStore dailyRecordStore;
    private final UserContext userContext;
    private final BusinessDayClock businessDayClock;

    public ConversationFlowService(
            ConversationSessionStore conversationSessionStore,
            ConversationReplyService conversationReplyService,
            RecordGenerationService recordGenerationService,
            ConversationSummaryAsyncService conversationSummaryAsyncService,
            DailyRecordStore dailyRecordStore,
            UserContext userContext,
            BusinessDayClock businessDayClock
    ) {
        this.conversationSessionStore = conversationSessionStore;
        this.conversationReplyService = conversationReplyService;
        this.recordGenerationService = recordGenerationService;
        this.conversationSummaryAsyncService = conversationSummaryAsyncService;
        this.dailyRecordStore = dailyRecordStore;
        this.userContext = userContext;
        this.businessDayClock = businessDayClock;
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
        return new CreateConversationResponse(session.id(), session.status(), toApiStatus(resolveSummaryStatus(session), session));
    }

    public SendMessageResponse sendMessage(String sessionId, SendMessageRequest request) {
        SendMessageResponse response = conversationReplyService.sendMessage(sessionId, request);
        ConversationSession session = conversationSessionStore.getSession(sessionId);
        return new SendMessageResponse(
                response.sessionId(),
                response.userMessage(),
                response.assistantReply(),
                response.assistantAudioUrl(),
                response.shouldEnd(),
                response.stage(),
                response.dominantMode(),
                response.reflectionReadiness(),
                toApiStatus(resolveSummaryStatus(session), session)
        );
    }

    public RequestSummaryResponse requestSummary(String sessionId) {
        ConversationSession session = conversationSessionStore.getSession(sessionId);
        ConversationSummaryStatus summaryStatus = resolveSummaryStatus(session);
        if ("recordGenerating".equals(summaryStatus.status())) {
            return new RequestSummaryResponse(sessionId, "正在整理刚才的内容", toApiStatus(summaryStatus, session));
        }
        if (session.userMessageCount() <= session.summarizedUserMessageCount()) {
            return new RequestSummaryResponse(sessionId, "当前没有需要整理的新内容", toApiStatus(summaryStatus, session));
        }
        ConversationSession runningSession = conversationSessionStore.markSummaryJobRunning(sessionId);
        conversationSummaryAsyncService.generateSummary(sessionId, runningSession.summaryJobTargetUserMessageCount());
        return new RequestSummaryResponse(
                sessionId,
                "正在整理刚才的内容",
                toApiStatus(resolveSummaryStatus(runningSession), runningSession)
        );
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
                "messages", conversationSessionStore.getMessages(sessionId),
                "summaryStatus", toApiStatus(resolveSummaryStatus(session), session)
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
                "messages", conversationSessionStore.getMessages(session.id()),
                "summaryStatus", toApiStatus(resolveSummaryStatus(session), session)
        );
    }

    public ConversationHistoryResponse getConversationHistory() {
        String userId = userContext.getCurrentUserId();
        LocalDate today = recordGenerationService.currentBusinessDate();
        List<ConversationHistoryItemResponse> recent7Days = new ArrayList<>();
        List<ConversationHistoryItemResponse> recent30Days = new ArrayList<>();
        List<ConversationHistoryItemResponse> recentHalfYear = new ArrayList<>();
        int frozenCount = 0;

        for (ConversationSession session : conversationSessionStore.listSessions(userId)) {
            LocalDate businessDate = sessionBusinessDate(session);
            long ageDays = ChronoUnit.DAYS.between(businessDate, today);
            if (ageDays < 0) {
                ageDays = 0;
            }
            if (ageDays > 183) {
                frozenCount += 1;
                continue;
            }
            ConversationHistoryItemResponse item = toHistoryItem(session, businessDate);
            if (ageDays <= 6) {
                recent7Days.add(item);
            } else if (ageDays <= 29) {
                recent30Days.add(item);
            } else {
                recentHalfYear.add(item);
            }
        }

        return new ConversationHistoryResponse(
                List.of(
                        new ConversationHistoryGroupResponse("recent7", "最近 7 天", recent7Days),
                        new ConversationHistoryGroupResponse("recent30", "最近 30 天", recent30Days),
                        new ConversationHistoryGroupResponse("recentHalfYear", "最近半年", recentHalfYear)
                ),
                frozenCount
        );
    }

    private ConversationSummaryStatus resolveSummaryStatus(ConversationSession session) {
        LocalDate today = recordGenerationService.currentBusinessDate();
        DailyRecord todayRecord = dailyRecordStore.findRecordByUserAndDate(userContext.getCurrentUserId(), today);
        if ("running".equalsIgnoreCase(session.summaryJobStatus())) {
            return new ConversationSummaryStatus("recordGenerating", todayRecord == null ? null : todayRecord.id());
        }
        if (todayRecord == null) {
            return new ConversationSummaryStatus("noRecordYet", null);
        }
        if (session.userMessageCount() > session.summarizedUserMessageCount()) {
            return new ConversationSummaryStatus("recordNeedsRefresh", todayRecord.id());
        }
        return new ConversationSummaryStatus("recordUpToDate", todayRecord.id());
    }

    private ConversationSummaryStatusResponse toApiStatus(ConversationSummaryStatus status) {
        return toApiStatus(status, null);
    }

    private ConversationSummaryStatusResponse toApiStatus(ConversationSummaryStatus status, ConversationSession session) {
        return new ConversationSummaryStatusResponse(
                status.status(),
                status.recordId(),
                session == null ? 0 : session.userMessageCount(),
                session == null ? 0 : session.summarizedUserMessageCount()
        );
    }

    private LocalDate sessionBusinessDate(ConversationSession session) {
        return businessDayClock.toBusinessDate(session.startedAt());
    }

    private ConversationHistoryItemResponse toHistoryItem(ConversationSession session, LocalDate businessDate) {
        List<ConversationMessage> messages = conversationSessionStore.getMessages(session.id());
        String preview = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ConversationMessage::text)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse("还没有留下内容");
        if (preview.length() > 42) {
            preview = preview.substring(0, 42) + "...";
        }
        return new ConversationHistoryItemResponse(
                session.id(),
                session.status(),
                businessDate,
                session.startedAt(),
                session.endedAt(),
                messages.size(),
                session.userMessageCount(),
                preview
        );
    }
}
