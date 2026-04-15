package com.nightlypick.server.conversation.application.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nightlypick.server.conversation.domain.ConversationMessage;
import com.nightlypick.server.conversation.domain.ConversationSession;
import com.nightlypick.server.common.time.BusinessDayClock;
import com.nightlypick.server.persistence.entity.ConversationMessageEntity;
import com.nightlypick.server.persistence.entity.ConversationSessionEntity;
import com.nightlypick.server.persistence.entity.DailyRecordEntity;
import com.nightlypick.server.persistence.entity.MemoryItemEntity;
import com.nightlypick.server.persistence.mapper.ConversationMessageMapper;
import com.nightlypick.server.persistence.mapper.ConversationSessionMapper;
import com.nightlypick.server.persistence.mapper.DailyRecordMapper;
import com.nightlypick.server.persistence.mapper.MemoryItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class ConversationSessionStore {
    private static final Logger log = LoggerFactory.getLogger(ConversationSessionStore.class);
    private final BusinessDayClock businessDayClock;
    private final ConversationSessionMapper sessionMapper;
    private final ConversationMessageMapper messageMapper;
    private final DailyRecordMapper recordMapper;
    private final MemoryItemMapper memoryItemMapper;

    public ConversationSessionStore(
            BusinessDayClock businessDayClock,
            ConversationSessionMapper sessionMapper,
            ConversationMessageMapper messageMapper,
            DailyRecordMapper recordMapper,
            MemoryItemMapper memoryItemMapper
    ) {
        this.businessDayClock = businessDayClock;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.recordMapper = recordMapper;
        this.memoryItemMapper = memoryItemMapper;
    }

    public ConversationSession createSession(String userId) {
        ConversationSessionEntity entity = new ConversationSessionEntity();
        entity.setId("session-" + UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStatus("active");
        entity.setStartedAt(OffsetDateTime.now());
        entity.setUserMessageCount(0);
        entity.setSummarizedUserMessageCount(0);
        entity.setSummaryJobStatus("idle");
        entity.setSummaryJobTargetUserMessageCount(0);
        sessionMapper.insert(entity);
        log.info("已创建会话实体 sessionId={} userId={} startedAt={}", entity.getId(), userId, entity.getStartedAt());
        return toDomain(entity);
    }

    public ConversationSession getSession(String sessionId) {
        ConversationSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }
        return toDomain(entity);
    }

    public ConversationSession findActiveSession(String userId) {
        var businessDate = businessDayClock.currentBusinessDate();
        List<ConversationSessionEntity> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ConversationSessionEntity>()
                        .eq(ConversationSessionEntity::getUserId, userId)
                        .eq(ConversationSessionEntity::getStatus, "active")
                        .orderByDesc(ConversationSessionEntity::getStartedAt)
        );
        ConversationSession session = sessions.stream()
                .filter(entity -> businessDayClock.toBusinessDate(entity.getStartedAt()).isEqual(businessDate))
                .findFirst()
                .map(this::toDomain)
                .orElse(null);
        log.info("查询活跃会话结果 userId={} businessDate={} candidateCount={} sessionId={}",
                userId,
                businessDate,
                sessions.size(),
                session == null ? null : session.id());
        return session;
    }

    public List<ConversationSession> listSessions(String userId) {
        return sessionMapper.selectList(
                        new LambdaQueryWrapper<ConversationSessionEntity>()
                                .eq(ConversationSessionEntity::getUserId, userId)
                                .orderByDesc(ConversationSessionEntity::getStartedAt)
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    public void completeSession(String sessionId) {
        ConversationSession session = getSession(sessionId);
        ConversationSessionEntity entity = new ConversationSessionEntity();
        entity.setId(session.id());
        entity.setUserId(session.userId());
        entity.setStatus("completed");
        entity.setStartedAt(session.startedAt());
        entity.setEndedAt(OffsetDateTime.now());
        entity.setUserMessageCount(session.userMessageCount());
        entity.setSummarizedUserMessageCount(session.summarizedUserMessageCount());
        entity.setSummaryJobStatus(session.summaryJobStatus());
        entity.setSummaryJobTargetUserMessageCount(session.summaryJobTargetUserMessageCount());
        sessionMapper.updateById(entity);
        log.info("已完成会话 sessionId={} endedAt={}", sessionId, entity.getEndedAt());
    }

    public ConversationMessage addMessage(String sessionId, String role, String inputType, String text) {
        getSession(sessionId);
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId("message-" + UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setInputType(inputType);
        entity.setText(text);
        entity.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(entity);
        if ("user".equals(role)) {
            incrementUserMessageCount(sessionId);
        }
        log.info("已写入会话消息 sessionId={} role={} inputType={} textLength={}",
                sessionId,
                role,
                inputType,
                text == null ? 0 : text.length());
        return toDomain(entity);
    }

    public ConversationSession markSummaryJobRunning(String sessionId) {
        ConversationSession session = getSession(sessionId);
        ConversationSessionEntity entity = copySession(session);
        entity.setSummaryJobStatus("running");
        entity.setSummaryJobTargetUserMessageCount(session.userMessageCount());
        sessionMapper.updateById(entity);
        log.info("会话总结任务已标记为运行中 sessionId={} targetUserMessageCount={}", sessionId, entity.getSummaryJobTargetUserMessageCount());
        return toDomain(entity);
    }

    public void markSummaryJobFinished(String sessionId, int summarizedUserMessageCount) {
        ConversationSession session = getSession(sessionId);
        ConversationSessionEntity entity = copySession(session);
        entity.setSummaryJobStatus("idle");
        entity.setSummarizedUserMessageCount(Math.max(session.summarizedUserMessageCount(), summarizedUserMessageCount));
        entity.setSummaryJobTargetUserMessageCount(0);
        sessionMapper.updateById(entity);
        log.info("会话总结任务已完成 sessionId={} summarizedUserMessageCount={}", sessionId, entity.getSummarizedUserMessageCount());
    }

    public void markSummaryJobFailed(String sessionId) {
        ConversationSession session = getSession(sessionId);
        ConversationSessionEntity entity = copySession(session);
        entity.setSummaryJobStatus("idle");
        entity.setSummaryJobTargetUserMessageCount(0);
        sessionMapper.updateById(entity);
        log.info("会话总结任务已重置为 idle sessionId={}", sessionId);
    }

    public List<ConversationMessage> getMessages(String sessionId) {
        getSession(sessionId);
        return messageMapper.selectList(
                        new LambdaQueryWrapper<ConversationMessageEntity>()
                                .eq(ConversationMessageEntity::getSessionId, sessionId)
                                .orderByAsc(ConversationMessageEntity::getCreatedAt)
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    public void clearHistory(String userId) {
        log.info("开始清理用户历史数据 userId={}", userId);
        List<DailyRecordEntity> records = recordMapper.selectList(
                new LambdaQueryWrapper<DailyRecordEntity>().eq(DailyRecordEntity::getUserId, userId)
        );
        List<String> recordIds = records.stream().map(DailyRecordEntity::getId).toList();
        if (!recordIds.isEmpty()) {
            memoryItemMapper.delete(new QueryWrapper<MemoryItemEntity>().in("source_record_id", recordIds));
            recordMapper.delete(new QueryWrapper<DailyRecordEntity>().in("id", recordIds));
        }

        List<ConversationSessionEntity> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ConversationSessionEntity>()
                        .eq(ConversationSessionEntity::getUserId, userId)
        );
        List<String> sessionIds = sessions.stream().map(ConversationSessionEntity::getId).toList();
        if (!sessionIds.isEmpty()) {
            messageMapper.delete(new QueryWrapper<ConversationMessageEntity>().in("session_id", sessionIds));
            sessionMapper.delete(new QueryWrapper<ConversationSessionEntity>().in("id", sessionIds));
        }
        log.info("用户历史数据清理完成 userId={} recordCount={} sessionCount={}", userId, recordIds.size(), sessionIds.size());
    }

    private ConversationSession toDomain(ConversationSessionEntity entity) {
        return new ConversationSession(
                entity.getId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getUserMessageCount() == null ? 0 : entity.getUserMessageCount(),
                entity.getSummarizedUserMessageCount() == null ? 0 : entity.getSummarizedUserMessageCount(),
                entity.getSummaryJobStatus() == null || entity.getSummaryJobStatus().isBlank() ? "idle" : entity.getSummaryJobStatus(),
                entity.getSummaryJobTargetUserMessageCount() == null ? 0 : entity.getSummaryJobTargetUserMessageCount()
        );
    }

    private ConversationMessage toDomain(ConversationMessageEntity entity) {
        return new ConversationMessage(
                entity.getId(),
                entity.getSessionId(),
                entity.getRole(),
                entity.getInputType(),
                entity.getText(),
                entity.getCreatedAt()
        );
    }

    private void incrementUserMessageCount(String sessionId) {
        ConversationSession session = getSession(sessionId);
        ConversationSessionEntity entity = copySession(session);
        entity.setUserMessageCount(session.userMessageCount() + 1);
        sessionMapper.updateById(entity);
    }

    private ConversationSessionEntity copySession(ConversationSession session) {
        ConversationSessionEntity entity = new ConversationSessionEntity();
        entity.setId(session.id());
        entity.setUserId(session.userId());
        entity.setStatus(session.status());
        entity.setStartedAt(session.startedAt());
        entity.setEndedAt(session.endedAt());
        entity.setUserMessageCount(session.userMessageCount());
        entity.setSummarizedUserMessageCount(session.summarizedUserMessageCount());
        entity.setSummaryJobStatus(session.summaryJobStatus());
        entity.setSummaryJobTargetUserMessageCount(session.summaryJobTargetUserMessageCount());
        return entity;
    }
}
