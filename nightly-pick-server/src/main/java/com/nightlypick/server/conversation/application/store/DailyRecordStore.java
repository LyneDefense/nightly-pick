package com.nightlypick.server.conversation.application.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightlypick.server.persistence.entity.DailyRecordEntity;
import com.nightlypick.server.persistence.mapper.DailyRecordMapper;
import com.nightlypick.server.record.domain.DailyRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Component
public class DailyRecordStore {
    private final DailyRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    public DailyRecordStore(DailyRecordMapper recordMapper, ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    public DailyRecord saveRecord(DailyRecord record) {
        DailyRecordEntity entity = new DailyRecordEntity();
        entity.setId("record-" + java.util.UUID.randomUUID());
        entity.setUserId(record.userId());
        entity.setSessionId(record.sessionId());
        entity.setRecordDate(record.date());
        entity.setTitle(record.title());
        entity.setSummary(record.summary());
        entity.setEventsJson(writeList(record.events()));
        entity.setEmotionsJson(writeList(record.emotions()));
        entity.setOpenLoopsJson(writeList(record.openLoops()));
        entity.setHighlight(record.highlight());
        entity.setCreatedAt(record.createdAt());
        recordMapper.insert(entity);
        return toDomain(entity);
    }

    public DailyRecord findRecordByUserAndDate(String userId, LocalDate date) {
        DailyRecordEntity entity = recordMapper.selectOne(
                new LambdaQueryWrapper<DailyRecordEntity>()
                        .eq(DailyRecordEntity::getUserId, userId)
                        .eq(DailyRecordEntity::getRecordDate, date)
                        .last("limit 1")
        );
        return entity == null ? null : toDomain(entity);
    }

    public DailyRecord updateGeneratedRecord(
            String recordId,
            String sessionId,
            String title,
            String summary,
            List<String> events,
            List<String> emotions,
            List<String> openLoops,
            String highlight
    ) {
        DailyRecord current = getRecord(recordId);
        DailyRecordEntity entity = new DailyRecordEntity();
        entity.setId(current.id());
        entity.setUserId(current.userId());
        entity.setSessionId(sessionId == null || sessionId.isBlank() ? current.sessionId() : sessionId);
        entity.setRecordDate(current.date());
        entity.setTitle(title == null || title.isBlank() ? current.title() : title);
        entity.setSummary(summary == null || summary.isBlank() ? current.summary() : summary);
        entity.setEventsJson(writeList(events == null || events.isEmpty() ? current.events() : events));
        entity.setEmotionsJson(writeList(emotions == null || emotions.isEmpty() ? current.emotions() : emotions));
        entity.setOpenLoopsJson(writeList(openLoops == null || openLoops.isEmpty() ? current.openLoops() : openLoops));
        entity.setHighlight(highlight == null || highlight.isBlank() ? current.highlight() : highlight);
        entity.setCreatedAt(current.createdAt());
        recordMapper.updateById(entity);
        return toDomain(entity);
    }

    public List<DailyRecord> listRecords() {
        return recordMapper.selectList(
                        new QueryWrapper<DailyRecordEntity>().orderByDesc("created_at")
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    public DailyRecord getRecord(String recordId) {
        DailyRecordEntity entity = recordMapper.selectById(recordId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + recordId);
        }
        return toDomain(entity);
    }

    public DailyRecord updateRecord(String recordId, String title, String summary) {
        DailyRecord current = getRecord(recordId);
        DailyRecordEntity entity = new DailyRecordEntity();
        entity.setId(current.id());
        entity.setUserId(current.userId());
        entity.setSessionId(current.sessionId());
        entity.setRecordDate(current.date());
        entity.setTitle(title == null || title.isBlank() ? current.title() : title);
        entity.setSummary(summary == null || summary.isBlank() ? current.summary() : summary);
        entity.setEventsJson(writeList(current.events()));
        entity.setEmotionsJson(writeList(current.emotions()));
        entity.setOpenLoopsJson(writeList(current.openLoops()));
        entity.setHighlight(current.highlight());
        entity.setCreatedAt(current.createdAt());
        recordMapper.updateById(entity);
        return toDomain(entity);
    }

    public void deleteRecord(String recordId) {
        DailyRecordEntity entity = recordMapper.selectById(recordId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + recordId);
        }
        recordMapper.deleteById(recordId);
    }

    private DailyRecord toDomain(DailyRecordEntity entity) {
        return new DailyRecord(
                entity.getId(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getRecordDate(),
                entity.getTitle(),
                entity.getSummary(),
                readList(entity.getEventsJson()),
                readList(entity.getEmotionsJson()),
                readList(entity.getOpenLoopsJson()),
                entity.getHighlight(),
                entity.getCreatedAt()
        );
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize list field.", exception);
        }
    }

    private List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize list field.", exception);
        }
    }
}
