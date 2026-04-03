package com.nightlypick.server.conversation.application.service;

import com.nightlypick.server.agent.dto.AgentExtractMemoryRequest;
import com.nightlypick.server.agent.dto.AgentGenerateRecordResponse;
import com.nightlypick.server.agent.dto.AgentPlanReflectionRequest;
import com.nightlypick.server.agent.dto.AgentPlanReflectionResponse;
import com.nightlypick.server.agent.dto.AgentWriteReflectionRequest;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.common.time.BusinessDayClock;
import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import com.nightlypick.server.conversation.application.store.DailyRecordStore;
import com.nightlypick.server.conversation.application.store.MemoryStore;
import com.nightlypick.server.conversation.domain.ConversationMessage;
import com.nightlypick.server.record.domain.DailyRecord;
import com.nightlypick.server.user.application.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecordGenerationService {
    private static final Logger log = LoggerFactory.getLogger(RecordGenerationService.class);
    private final ConversationSessionStore conversationSessionStore;
    private final DailyRecordStore dailyRecordStore;
    private final MemoryStore memoryStore;
    private final AgentClient agentClient;
    private final UserContext userContext;
    private final BusinessDayClock businessDayClock;

    public RecordGenerationService(
            ConversationSessionStore conversationSessionStore,
            DailyRecordStore dailyRecordStore,
            MemoryStore memoryStore,
            AgentClient agentClient,
            UserContext userContext,
            BusinessDayClock businessDayClock
    ) {
        this.conversationSessionStore = conversationSessionStore;
        this.dailyRecordStore = dailyRecordStore;
        this.memoryStore = memoryStore;
        this.agentClient = agentClient;
        this.userContext = userContext;
        this.businessDayClock = businessDayClock;
    }

    public SavedRecordResult saveOrUpdateDailyRecord(String sessionId, boolean markSessionCompleted) {
        log.info("开始整理今夜记录 sessionId={} markSessionCompleted={}", sessionId, markSessionCompleted);
        List<ConversationMessage> messages = conversationSessionStore.getMessages(sessionId);
        boolean hasUserContent = messages.stream()
                .anyMatch(message -> "user".equals(message.role()) && message.text() != null && !message.text().isBlank());
        if (!hasUserContent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No user content to summarize.");
        }
        String userId = userContext.getCurrentUserId();
        String conversationText = messages.stream()
                .map(message -> message.role() + ": " + message.text())
                .collect(Collectors.joining("\n"));
        LocalDate today = businessDayClock.currentBusinessDate();
        DailyRecord existingTodayRecord = dailyRecordStore.findRecordByUserAndDate(userId, today);
        log.info("已汇总生成记录上下文 sessionId={} userId={} messageCount={} businessDate={} hasExistingRecord={}",
                sessionId,
                userId,
                messages.size(),
                today,
                existingTodayRecord != null);

        AgentPlanReflectionResponse planned = normalizeReflectionPlan(agentClient.planReflection(
                new AgentPlanReflectionRequest(
                        sessionId,
                        conversationText,
                        existingTodayRecord == null ? null : existingTodayRecord.title(),
                        existingTodayRecord == null ? null : existingTodayRecord.summary(),
                        existingTodayRecord == null ? null : existingTodayRecord.highlight(),
                        existingTodayRecord == null ? List.of() : existingTodayRecord.events(),
                        existingTodayRecord == null ? List.of() : existingTodayRecord.emotions(),
                        existingTodayRecord == null ? List.of() : existingTodayRecord.openLoops()
                )
        ), userMessages(messages));
        log.info("Reflection planner 完成 sessionId={} depth={} tone={} shape={} facts={} unfinished={} focusCount={}",
                sessionId,
                planned.reflectionDepth(),
                planned.tone(),
                planned.recordShape(),
                planned.shouldListFacts(),
                planned.shouldListUnfinished(),
                planned.focus().size());
        AgentGenerateRecordResponse generated = agentClient.writeReflection(
                new AgentWriteReflectionRequest(
                        sessionId,
                        conversationText,
                        existingTodayRecord == null ? null : existingTodayRecord.title(),
                        existingTodayRecord == null ? null : existingTodayRecord.summary(),
                        existingTodayRecord == null ? null : existingTodayRecord.highlight(),
                        existingTodayRecord == null ? List.of() : existingTodayRecord.events(),
                        existingTodayRecord == null ? List.of() : existingTodayRecord.emotions(),
                        existingTodayRecord == null ? List.of() : existingTodayRecord.openLoops(),
                        planned
                )
        );
        log.info("Reflection writer 完成 sessionId={} title={} summaryLength={} eventCount={} emotionCount={} openLoopCount={}",
                sessionId,
                generated.title(),
                generated.summary() == null ? 0 : generated.summary().length(),
                generated.events() == null ? 0 : generated.events().size(),
                generated.emotions() == null ? 0 : generated.emotions().size(),
                generated.openLoops() == null ? 0 : generated.openLoops().size());
        NormalizedRecordContent normalized = normalizeGeneratedRecord(generated, messages, existingTodayRecord);
        log.info("记录结果规范化完成 sessionId={} title={} summaryLength={} eventCount={} emotionCount={} openLoopCount={}",
                sessionId,
                normalized.title(),
                normalized.summary().length(),
                normalized.events().size(),
                normalized.emotions().size(),
                normalized.openLoops().size());
        DailyRecord savedRecord;
        if (existingTodayRecord == null) {
            savedRecord = dailyRecordStore.saveRecord(new DailyRecord(
                    null,
                    userId,
                    sessionId,
                    today,
                    normalized.title(),
                    normalized.summary(),
                    normalized.events(),
                    normalized.emotions(),
                    normalized.openLoops(),
                    normalized.highlight(),
                    OffsetDateTime.now()
            ));
            log.info("已创建新的今夜记录 sessionId={} recordId={} recordDate={}", sessionId, savedRecord.id(), today);
        } else {
            savedRecord = dailyRecordStore.updateGeneratedRecord(
                    existingTodayRecord.id(),
                    sessionId,
                    normalized.title(),
                    normalized.summary(),
                    normalized.events(),
                    normalized.emotions(),
                    normalized.openLoops(),
                    normalized.highlight()
            );
            memoryStore.deleteMemoryItemsByRecordId(savedRecord.id());
            log.info("已更新当天已有记录 sessionId={} recordId={} recordDate={}", sessionId, savedRecord.id(), today);
        }
        if (!normalized.summary().isBlank()) {
            var memoryResponse = agentClient.extractMemory(
                    new AgentExtractMemoryRequest(
                            savedRecord.id(),
                            savedRecord.summary(),
                            conversationText,
                            memoryStore.listMemoryMergeCandidates(userId, 12)
                    ));
            memoryResponse.shortTermMemory().forEach(item ->
                    memoryStore.saveMemoryItem(userId, item.type(), item.content(), item.sourceRecordId()));
            log.info("记忆抽取完成 sessionId={} recordId={} memoryCount={}", sessionId, savedRecord.id(), memoryResponse.shortTermMemory().size());
        }
        if (markSessionCompleted) {
            conversationSessionStore.completeSession(sessionId);
            log.info("本次整理后已完成会话 sessionId={}", sessionId);
        }
        log.info("今夜记录整理流程结束 sessionId={} recordId={} merged={}", sessionId, savedRecord.id(), existingTodayRecord != null);
        return new SavedRecordResult(savedRecord, existingTodayRecord != null);
    }

    public LocalDate currentBusinessDate() {
        return businessDayClock.currentBusinessDate();
    }

    private NormalizedRecordContent normalizeGeneratedRecord(
            AgentGenerateRecordResponse generated,
            List<ConversationMessage> messages,
            DailyRecord existingTodayRecord
    ) {
        List<String> userMessages = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ConversationMessage::text)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim)
                .toList();

        String fallbackSummary = buildFallbackSummary(userMessages, existingTodayRecord);
        String summary = sanitizeParagraph(generated.summary());
        if (summary.isBlank()) {
            summary = fallbackSummary;
        } else if (summary.length() < 18 && !fallbackSummary.isBlank()) {
            summary = summary + " " + fallbackSummary;
        }

        List<String> events = normalizeList(generated.events(), 4);
        if (events.isEmpty()) {
            events = buildFallbackEvents(userMessages);
        }

        List<String> emotions = normalizeList(generated.emotions(), 4);
        if (emotions.isEmpty() && existingTodayRecord != null) {
            emotions = normalizeList(existingTodayRecord.emotions(), 4);
        }

        List<String> openLoops = normalizeList(generated.openLoops(), 4);
        String highlight = sanitizeSentence(generated.highlight());
        if (highlight.isBlank()) {
            highlight = firstMeaningfulLine(summary);
        }

        String title = sanitizeTitle(generated.title());
        if (title.isBlank()) {
            title = buildFallbackTitle(summary, userMessages, emotions);
        }

        return new NormalizedRecordContent(title, summary, events, emotions, openLoops, highlight);
    }

    private AgentPlanReflectionResponse normalizeReflectionPlan(
            AgentPlanReflectionResponse planned,
            List<String> userMessages
    ) {
        Set<String> allowedDepths = Set.of("companionship", "light", "medium", "deep");
        Set<String> allowedTones = Set.of("quiet-companionship", "gentle-reflection", "clear-reflection");
        Set<String> allowedShapes = Set.of("light_record", "standard_record", "deep_record");
        String depth = planned == null || planned.reflectionDepth() == null ? "" : planned.reflectionDepth().trim();
        String tone = planned == null || planned.tone() == null ? "" : planned.tone().trim();
        String shape = planned == null || planned.recordShape() == null ? "" : planned.recordShape().trim();

        boolean hasTodaySignals = userMessages.stream().anyMatch(text -> text.contains("今天") || text.contains("今晚"));
        boolean hasUnfinishedSignals = userMessages.stream().anyMatch(text -> text.contains("本来想") || text.contains("没做") || text.contains("还没"));
        String fallbackDepth = hasTodaySignals ? (hasUnfinishedSignals ? "deep" : "medium") : "light";
        String fallbackTone = "companionship".equals(fallbackDepth) ? "quiet-companionship"
                : "deep".equals(fallbackDepth) ? "clear-reflection" : "gentle-reflection";
        String fallbackShape = "companionship".equals(fallbackDepth) ? "light_record"
                : "deep".equals(fallbackDepth) ? "deep_record" : "standard_record";

        return new AgentPlanReflectionResponse(
                allowedDepths.contains(depth) ? depth : fallbackDepth,
                allowedTones.contains(tone) ? tone : fallbackTone,
                planned != null && planned.shouldListFacts(),
                planned != null && planned.shouldListUnfinished(),
                planned == null || planned.shouldMakeConclusion(),
                normalizeList(planned == null ? List.of() : planned.focus(), 4),
                normalizeList(planned == null ? List.of() : planned.whatHappenedToday(), 4),
                normalizeList(planned == null ? List.of() : planned.wantedButNotDone(), 4),
                sanitizeSentence(planned == null ? null : planned.coreTension()),
                allowedShapes.contains(shape) ? shape : fallbackShape
        );
    }

    private List<String> userMessages(List<ConversationMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ConversationMessage::text)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim)
                .toList();
    }

    private String buildFallbackSummary(List<String> userMessages, DailyRecord existingTodayRecord) {
        List<String> snippets = userMessages.stream()
                .filter(text -> text.length() >= 4)
                .limit(3)
                .map(this::sanitizeSentence)
                .toList();
        String freshPart = snippets.isEmpty() ? "今晚你留下了一次关于今天的复盘。" : String.join("；", snippets);
        if (existingTodayRecord != null && existingTodayRecord.summary() != null && !existingTodayRecord.summary().isBlank()) {
            return sanitizeParagraph(existingTodayRecord.summary() + "\n" + freshPart);
        }
        return sanitizeParagraph(freshPart);
    }

    private List<String> buildFallbackEvents(List<String> userMessages) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        userMessages.stream()
                .filter(text -> text.length() >= 4)
                .limit(3)
                .map(this::sanitizeSentence)
                .forEach(values::add);
        if (values.isEmpty()) {
            values.add("完成了一次关于今天的睡前复盘。");
        }
        return new ArrayList<>(values);
    }

    private List<String> normalizeList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::sanitizeSentence)
                .filter(value -> !value.isBlank())
                .forEach(normalized::add);
        return normalized.stream().limit(limit).toList();
    }

    private String sanitizeTitle(String value) {
        String title = sanitizeSentence(value);
        if (title.isBlank()) {
            return "";
        }
        title = title.replace("。", "").replace("，", " ");
        title = title.length() > 18 ? title.substring(0, 18) : title;
        return title.trim();
    }

    private String buildFallbackTitle(String summary, List<String> userMessages, List<String> emotions) {
        if (!emotions.isEmpty()) {
            return "今夜的" + emotions.get(0);
        }
        String firstUserLine = userMessages.stream()
                .map(this::sanitizeSentence)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");
        if (!firstUserLine.isBlank()) {
            return firstUserLine.length() > 12 ? firstUserLine.substring(0, 12) : firstUserLine;
        }
        String firstSummaryLine = firstMeaningfulLine(summary);
        if (!firstSummaryLine.isBlank()) {
            return firstSummaryLine.length() > 12 ? firstSummaryLine.substring(0, 12) : firstSummaryLine;
        }
        return "今夜记录";
    }

    private String sanitizeParagraph(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String sanitizeSentence(String value) {
        return sanitizeParagraph(value).replaceAll("\\n+", " ").trim();
    }

    private String firstMeaningfulLine(String value) {
        return sanitizeParagraph(value).lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");
    }

    public record SavedRecordResult(
            DailyRecord record,
            boolean merged
    ) {
    }

    private record NormalizedRecordContent(
            String title,
            String summary,
            List<String> events,
            List<String> emotions,
            List<String> openLoops,
            String highlight
    ) {
    }
}
