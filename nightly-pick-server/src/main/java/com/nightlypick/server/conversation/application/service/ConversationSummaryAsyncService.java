package com.nightlypick.server.conversation.application.service;

import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ConversationSummaryAsyncService {
    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryAsyncService.class);

    private final RecordGenerationService recordGenerationService;
    private final ConversationSessionStore conversationSessionStore;

    public ConversationSummaryAsyncService(
            RecordGenerationService recordGenerationService,
            ConversationSessionStore conversationSessionStore
    ) {
        this.recordGenerationService = recordGenerationService;
        this.conversationSessionStore = conversationSessionStore;
    }

    @Async("summaryTaskExecutor")
    public void generateSummary(String sessionId, int targetUserMessageCount) {
        try {
            log.info("开始异步整理今夜记录 sessionId={} targetUserMessageCount={}", sessionId, targetUserMessageCount);
            recordGenerationService.saveOrUpdateDailyRecord(sessionId, false);
            conversationSessionStore.markSummaryJobFinished(sessionId, targetUserMessageCount);
            log.info("异步整理今夜记录完成 sessionId={} targetUserMessageCount={}", sessionId, targetUserMessageCount);
        } catch (RuntimeException error) {
            log.error("异步整理今夜记录失败 sessionId={}", sessionId, error);
            conversationSessionStore.markSummaryJobFailed(sessionId);
        }
    }
}
