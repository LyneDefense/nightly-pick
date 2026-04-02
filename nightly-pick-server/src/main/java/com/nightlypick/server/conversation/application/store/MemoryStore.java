package com.nightlypick.server.conversation.application.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightlypick.server.memory.domain.MemoryItem;
import com.nightlypick.server.persistence.entity.DailyRecordEntity;
import com.nightlypick.server.persistence.entity.MemoryItemEntity;
import com.nightlypick.server.persistence.mapper.DailyRecordMapper;
import com.nightlypick.server.persistence.mapper.MemoryItemMapper;
import com.nightlypick.server.record.domain.DailyRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MemoryStore {
    private static final Pattern NON_TOPIC_PATTERN = Pattern.compile("[^a-z0-9\\u4e00-\\u9fa5]+");

    private final MemoryItemMapper memoryItemMapper;
    private final DailyRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    public MemoryStore(MemoryItemMapper memoryItemMapper, DailyRecordMapper recordMapper, ObjectMapper objectMapper) {
        this.memoryItemMapper = memoryItemMapper;
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    public MemoryItem saveMemoryItem(String userId, String type, String content, String sourceRecordId) {
        String normalizedType = normalizeType(type);
        String normalizedContent = normalizeContent(content);
        if (normalizedContent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory content cannot be blank.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        String topicKey = buildTopicKey(normalizedType, normalizedContent);
        MemoryItemEntity similar = findSimilarMemoryEntity(userId, normalizedType, normalizedContent, topicKey);
        if (similar != null) {
            similar.setContent(chooseBetterMemoryContent(similar.getContent(), normalizedContent));
            similar.setTopicKey(similar.getTopicKey() == null || similar.getTopicKey().isBlank() ? topicKey : similar.getTopicKey());
            similar.setMentionCount((similar.getMentionCount() == null ? 0 : similar.getMentionCount()) + 1);
            similar.setImportanceScore(calculateImportanceScore(similar.getMentionCount(), similar.getFirstSeenAt(), now));
            similar.setSourceRecordId(sourceRecordId);
            similar.setLastSeenAt(now);
            memoryItemMapper.updateById(similar);
            return toDomain(similar);
        }

        MemoryItemEntity entity = new MemoryItemEntity();
        entity.setId("memory-" + UUID.randomUUID());
        entity.setUserId(userId);
        entity.setType(normalizedType);
        entity.setContent(normalizedContent);
        entity.setTopicKey(topicKey);
        entity.setMentionCount(1);
        entity.setImportanceScore(1.0d);
        entity.setSourceRecordId(sourceRecordId);
        entity.setCreatedAt(now);
        entity.setFirstSeenAt(now);
        entity.setLastSeenAt(now);
        memoryItemMapper.insert(entity);
        return toDomain(entity);
    }

    public void deleteMemoryItemsByRecordId(String recordId) {
        memoryItemMapper.delete(new LambdaQueryWrapper<MemoryItemEntity>()
                .eq(MemoryItemEntity::getSourceRecordId, recordId));
    }

    public List<MemoryItem> listMemories(String userId) {
        return memoryItemMapper.selectList(
                        new LambdaQueryWrapper<MemoryItemEntity>()
                                .eq(MemoryItemEntity::getUserId, userId)
                                .orderByDesc(MemoryItemEntity::getImportanceScore)
                                .orderByDesc(MemoryItemEntity::getLastSeenAt)
                ).stream()
                .filter(this::isActiveMemory)
                .sorted(Comparator.comparingDouble((MemoryItemEntity item) -> effectiveMemoryScore(item)).reversed())
                .map(this::toDomain)
                .toList();
    }

    public List<String> listRelevantMemoryContents(String userId, String userInput, int limit) {
        String normalizedInput = normalizeContent(userInput);
        List<MemoryItemEntity> candidates = memoryItemMapper.selectList(
                new LambdaQueryWrapper<MemoryItemEntity>()
                        .eq(MemoryItemEntity::getUserId, userId)
                        .orderByDesc(MemoryItemEntity::getImportanceScore)
                        .orderByDesc(MemoryItemEntity::getLastSeenAt)
                        .last("limit 50")
        );
        return candidates.stream()
                .filter(this::isActiveMemory)
                .sorted(Comparator.comparingDouble((MemoryItemEntity item) -> memoryRecallScore(item, normalizedInput)).reversed())
                .filter(item -> memoryRecallScore(item, normalizedInput) > 0.12d)
                .limit(limit)
                .map(MemoryItemEntity::getContent)
                .toList();
    }

    public List<String> listMemoryMergeCandidates(String userId, int limit) {
        return memoryItemMapper.selectList(
                        new LambdaQueryWrapper<MemoryItemEntity>()
                                .eq(MemoryItemEntity::getUserId, userId)
                                .orderByDesc(MemoryItemEntity::getImportanceScore)
                                .orderByDesc(MemoryItemEntity::getLastSeenAt)
                                .last("limit " + limit)
                ).stream()
                .filter(this::isMergeCandidateMemory)
                .sorted(Comparator.comparingDouble((MemoryItemEntity item) -> effectiveMemoryScore(item)).reversed())
                .limit(limit)
                .map(item -> "[" + item.getType() + "] " + item.getContent())
                .toList();
    }

    public String buildUserProfileSummary(String userId) {
        List<MemoryItem> memories = listMemories(userId);
        if (memories.isEmpty()) {
            return "";
        }
        List<String> topicItems = topMemoryContentsByType(memories, List.of("topic", "ongoing"), 3);
        List<String> emotionItems = topMemoryContentsByType(memories, List.of("emotion"), 3);
        List<String> personItems = topMemoryContentsByType(memories, List.of("person"), 2);
        List<String> soothingItems = topMemoryContentsByType(memories, List.of("soothing"), 2);
        List<String> lines = new ArrayList<>();
        if (!topicItems.isEmpty()) lines.add("持续议题：" + String.join("；", topicItems));
        if (!emotionItems.isEmpty()) lines.add("常见情绪：" + String.join("；", emotionItems));
        if (!personItems.isEmpty()) lines.add("在意的人或对象：" + String.join("；", personItems));
        if (!soothingItems.isEmpty()) lines.add("常见缓和方式：" + String.join("；", soothingItems));
        return String.join("\n", lines);
    }

    public String buildEmotionalTrendSummary(String userId) {
        List<DailyRecord> recentRecords = listRecentRecordsByUser(userId, 6);
        if (recentRecords.isEmpty()) {
            return "";
        }
        List<DailyRecord> latestWindow = recentRecords.subList(0, Math.min(3, recentRecords.size()));
        List<DailyRecord> earlierWindow = recentRecords.size() > 3 ? recentRecords.subList(3, recentRecords.size()) : List.of();
        Map<String, Long> latestEmotionCounts = countEmotions(latestWindow);
        Map<String, Long> earlierEmotionCounts = countEmotions(earlierWindow);
        List<String> lines = new ArrayList<>();

        List<String> repeatedRecentEmotions = latestEmotionCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(2)
                .toList();
        if (!repeatedRecentEmotions.isEmpty()) {
            lines.add("最近几次记录里，" + String.join("、", repeatedRecentEmotions) + "反复出现。");
        }

        DailyRecord latestRecord = latestWindow.get(0);
        List<String> latestEmotions = latestRecord.emotions() == null ? List.of() : latestRecord.emotions().stream().distinct().limit(3).toList();
        int latestOpenLoopCount = latestRecord.openLoops() == null ? 0 : latestRecord.openLoops().size();
        int earlierAverageEmotionCount = earlierWindow.isEmpty()
                ? 0
                : (int) Math.round(earlierWindow.stream()
                .mapToInt(record -> record.emotions() == null ? 0 : record.emotions().size())
                .average()
                .orElse(0.0d));

        if (!latestEmotions.isEmpty() && latestOpenLoopCount == 0 && earlierAverageEmotionCount >= latestEmotions.size() + 1) {
            lines.add("今晚的情绪层次比前几次收拢一些，主要落在" + String.join("、", latestEmotions) + "。");
        } else if (!latestEmotions.isEmpty()) {
            lines.add("今晚更明显的情绪落点是" + String.join("、", latestEmotions) + "。");
        }
        return String.join("\n", lines);
    }

    public String buildConversationStrategyHints(String userId, String userInput) {
        List<MemoryItem> memories = listMemories(userId);
        if (memories.isEmpty()) {
            return "";
        }
        String normalizedInput = normalizeContent(userInput);
        List<String> lines = new ArrayList<>();

        boolean hasRepeatedRelevantTopic = memories.stream()
                .filter(item -> List.of("topic", "ongoing").contains(item.type()))
                .anyMatch(item -> item.mentionCount() >= 2
                        && candidateSimilarity(toEntity(item), normalizedInput, buildTopicKey(item.type(), normalizedInput)) >= 0.2d);
        if (hasRepeatedRelevantTopic) {
            lines.add("这个议题已经反复出现，少问背景，多问这次和之前有什么不同。");
        }

        if (hasHighEmotionalLoad(userId)) {
            lines.add("先接住情绪，再只推进一个小问题，避免连续追问。");
        }

        List<String> soothingItems = topMemoryContentsByType(memories, List.of("soothing"), 2);
        if (!soothingItems.isEmpty()) {
            lines.add("如果用户有点卡住，可以轻轻提醒他熟悉的缓和方式，如：" + String.join("；", soothingItems) + "。");
        }

        if (lines.isEmpty()) {
            lines.add("如果引用历史，只引用最相关的一点，并把重点放回今晚。");
        }
        return String.join("\n", lines);
    }

    public void clearMemories(String userId) {
        memoryItemMapper.delete(new LambdaQueryWrapper<MemoryItemEntity>()
                .eq(MemoryItemEntity::getUserId, userId));
    }

    private List<String> topMemoryContentsByType(List<MemoryItem> memories, List<String> types, int limit) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        memories.stream()
                .filter(item -> types.contains(item.type()))
                .filter(this::isActiveMemory)
                .sorted(Comparator.comparingDouble((MemoryItem item) -> effectiveMemoryScore(item)).reversed()
                        .thenComparing(MemoryItem::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(MemoryItem::content)
                .forEach(ordered::add);
        return new ArrayList<>(ordered);
    }

    private List<DailyRecord> listRecentRecordsByUser(String userId, int limit) {
        return recordMapper.selectList(
                        new LambdaQueryWrapper<DailyRecordEntity>()
                                .eq(DailyRecordEntity::getUserId, userId)
                                .orderByDesc(DailyRecordEntity::getRecordDate)
                                .orderByDesc(DailyRecordEntity::getCreatedAt)
                                .last("limit " + limit)
                ).stream()
                .map(this::toDailyRecord)
                .toList();
    }

    private Map<String, Long> countEmotions(List<DailyRecord> records) {
        return records.stream()
                .flatMap(record -> (record.emotions() == null ? List.<String>of() : record.emotions()).stream())
                .map(this::normalizeContent)
                .filter(emotion -> !emotion.isBlank())
                .collect(Collectors.groupingBy(emotion -> emotion, Collectors.counting()));
    }

    private MemoryItemEntity findSimilarMemoryEntity(String userId, String type, String content, String topicKey) {
        List<MemoryItemEntity> candidates = memoryItemMapper.selectList(
                new LambdaQueryWrapper<MemoryItemEntity>()
                        .eq(MemoryItemEntity::getUserId, userId)
                        .eq(MemoryItemEntity::getType, type)
                        .orderByDesc(MemoryItemEntity::getImportanceScore)
                        .orderByDesc(MemoryItemEntity::getLastSeenAt)
                        .last("limit 20")
        );
        MemoryItemEntity best = null;
        double bestScore = 0.0d;
        for (MemoryItemEntity candidate : candidates) {
            if (!isMergeCandidateMemory(candidate)) continue;
            double score = candidateSimilarity(candidate, content, topicKey);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= 0.72d ? best : null;
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank() ? "topic" : type.trim().toLowerCase();
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }

    private String buildTopicKey(String type, String content) {
        String normalized = NON_TOPIC_PATTERN.matcher(content.toLowerCase()).replaceAll("");
        if (normalized.isBlank()) normalized = "empty";
        if (normalized.length() > 24) normalized = normalized.substring(0, 24);
        return type + ":" + normalized;
    }

    private String chooseBetterMemoryContent(String existing, String incoming) {
        if (existing == null || existing.isBlank()) return incoming;
        if (incoming == null || incoming.isBlank()) return existing;
        return incoming.length() > existing.length() ? incoming : existing;
    }

    private double calculateImportanceScore(Integer mentionCount, OffsetDateTime firstSeenAt, OffsetDateTime now) {
        int count = mentionCount == null ? 1 : mentionCount;
        long days = firstSeenAt == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(firstSeenAt.toLocalDate(), now.toLocalDate()));
        return count * 1.0d + Math.min(days, 14L) * 0.08d;
    }

    private double candidateSimilarity(MemoryItemEntity candidate, String content, String topicKey) {
        if (candidate.getTopicKey() != null && candidate.getTopicKey().equals(topicKey)) return 1.0d;
        String left = NON_TOPIC_PATTERN.matcher(normalizeContent(candidate.getContent()).toLowerCase()).replaceAll("");
        String right = NON_TOPIC_PATTERN.matcher(normalizeContent(content).toLowerCase()).replaceAll("");
        if (left.isBlank() || right.isBlank()) return 0.0d;
        if (left.contains(right) || right.contains(left)) return 0.9d;
        int overlap = 0;
        for (int index = 0; index < Math.min(left.length(), right.length()); index++) {
            if (right.indexOf(left.charAt(index)) >= 0) overlap++;
        }
        return (double) overlap / (double) Math.max(left.length(), right.length());
    }

    private MemoryItemEntity toEntity(MemoryItem item) {
        MemoryItemEntity entity = new MemoryItemEntity();
        entity.setId(item.id());
        entity.setUserId(item.userId());
        entity.setType(item.type());
        entity.setContent(item.content());
        entity.setTopicKey(item.topicKey());
        entity.setMentionCount(item.mentionCount());
        entity.setImportanceScore(item.importanceScore());
        entity.setSourceRecordId(item.sourceRecordId());
        entity.setCreatedAt(item.createdAt());
        entity.setFirstSeenAt(item.firstSeenAt());
        entity.setLastSeenAt(item.lastSeenAt());
        return entity;
    }

    private double memoryRecallScore(MemoryItemEntity item, String normalizedInput) {
        String content = normalizeContent(item.getContent());
        double similarity = candidateSimilarity(item, normalizedInput, buildTopicKey(item.getType(), normalizedInput));
        String cleanMemory = NON_TOPIC_PATTERN.matcher(content.toLowerCase()).replaceAll("");
        String cleanInput = NON_TOPIC_PATTERN.matcher(normalizedInput.toLowerCase()).replaceAll("");
        if (!cleanInput.isBlank() && !cleanMemory.isBlank() && (cleanMemory.contains(cleanInput) || cleanInput.contains(cleanMemory))) {
            similarity = Math.max(similarity, 0.95d);
        }
        double mentionBonus = Math.min((item.getMentionCount() == null ? 1 : item.getMentionCount()) * 0.08d, 0.4d);
        double importanceBonus = Math.min((item.getImportanceScore() == null ? 1.0d : item.getImportanceScore()) * 0.04d, 0.3d);
        double recencyBonus = 0.0d;
        if (item.getLastSeenAt() != null) {
            long days = Math.max(0L, ChronoUnit.DAYS.between(item.getLastSeenAt().toLocalDate(), LocalDate.now()));
            recencyBonus = Math.max(0.0d, 0.25d - days * 0.02d);
        }
        return similarity + mentionBonus + importanceBonus + recencyBonus + effectiveMemoryScore(item) * 0.05d;
    }

    private boolean isActiveMemory(MemoryItemEntity item) {
        long staleDays = staleDays(item.getLastSeenAt());
        int mentions = item.getMentionCount() == null ? 1 : item.getMentionCount();
        if (mentions >= 4) return staleDays <= 180L;
        if (mentions == 3) return staleDays <= 120L;
        if (mentions == 2) return staleDays <= 45L;
        return staleDays <= 18L;
    }

    private boolean isActiveMemory(MemoryItem item) {
        long staleDays = staleDays(item.lastSeenAt());
        int mentions = item.mentionCount();
        if (mentions >= 4) return staleDays <= 180L;
        if (mentions == 3) return staleDays <= 120L;
        if (mentions == 2) return staleDays <= 45L;
        return staleDays <= 18L;
    }

    private boolean isMergeCandidateMemory(MemoryItemEntity item) {
        long staleDays = staleDays(item.getLastSeenAt());
        int mentions = item.getMentionCount() == null ? 1 : item.getMentionCount();
        if (mentions >= 3) return staleDays <= 240L;
        return staleDays <= 60L;
    }

    private long staleDays(OffsetDateTime lastSeenAt) {
        if (lastSeenAt == null) return 0L;
        return Math.max(0L, ChronoUnit.DAYS.between(lastSeenAt.toLocalDate(), LocalDate.now()));
    }

    private double effectiveMemoryScore(MemoryItemEntity item) {
        double base = item.getImportanceScore() == null ? 1.0d : item.getImportanceScore();
        int mentions = item.getMentionCount() == null ? 1 : item.getMentionCount();
        long staleDays = staleDays(item.getLastSeenAt());
        double decayFactor = mentions >= 4 ? 0.01d : mentions == 3 ? 0.015d : mentions == 2 ? 0.03d : 0.06d;
        return Math.max(0.05d, base - staleDays * decayFactor);
    }

    private double effectiveMemoryScore(MemoryItem item) {
        double base = item.importanceScore();
        int mentions = item.mentionCount();
        long staleDays = staleDays(item.lastSeenAt());
        double decayFactor = mentions >= 4 ? 0.01d : mentions == 3 ? 0.015d : mentions == 2 ? 0.03d : 0.06d;
        return Math.max(0.05d, base - staleDays * decayFactor);
    }

    private boolean hasHighEmotionalLoad(String userId) {
        List<DailyRecord> recentRecords = listRecentRecordsByUser(userId, 3);
        if (recentRecords.isEmpty()) {
            return false;
        }
        DailyRecord latest = recentRecords.get(0);
        int emotionCount = latest.emotions() == null ? 0 : latest.emotions().size();
        int openLoopCount = latest.openLoops() == null ? 0 : latest.openLoops().size();
        long repeatedEmotionCount = recentRecords.stream()
                .flatMap(record -> (record.emotions() == null ? List.<String>of() : record.emotions()).stream())
                .map(this::normalizeContent)
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .values().stream()
                .filter(count -> count >= 2)
                .count();
        return emotionCount >= 3 || openLoopCount >= 2 || repeatedEmotionCount >= 2;
    }

    private MemoryItem toDomain(MemoryItemEntity entity) {
        return new MemoryItem(
                entity.getId(),
                entity.getUserId(),
                entity.getType(),
                entity.getContent(),
                entity.getTopicKey(),
                entity.getMentionCount() == null ? 1 : entity.getMentionCount(),
                entity.getImportanceScore() == null ? 1.0d : entity.getImportanceScore(),
                entity.getSourceRecordId(),
                entity.getCreatedAt(),
                entity.getFirstSeenAt(),
                entity.getLastSeenAt()
        );
    }

    private DailyRecord toDailyRecord(DailyRecordEntity entity) {
        return new DailyRecord(
                entity.getId(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getRecordDate(),
                entity.getTitle(),
                entity.getSummary(),
                parseJsonList(entity.getEventsJson()),
                parseJsonList(entity.getEmotionsJson()),
                parseJsonList(entity.getOpenLoopsJson()),
                entity.getHighlight(),
                entity.getCreatedAt()
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize list field.", exception);
        }
    }
}
