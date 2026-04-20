package com.nightlypick.server.common.timing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TimingLogService {
    private static final Logger timingLog = LoggerFactory.getLogger("com.nightlypick.server.conversation.event");
    private final ObjectMapper objectMapper;

    public TimingLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> step(String key, String label, long elapsedMs) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("key", key);
        step.put("label", label);
        step.put("elapsedMs", elapsedMs);
        return step;
    }

    public void conversationBegin(String sessionId, LocalDate businessDate, String userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("businessDate", businessDate == null ? null : businessDate.toString());
        payload.put("userId", userId);
        log("conversation_begin", "会话开始", payload);
    }

    public void turnBegin(String sessionId, String inputType, String inputText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("inputType", inputType);
        payload.put("inputLength", inputText == null ? 0 : inputText.length());
        payload.put("inputPreview", preview(inputText));
        log("turn_begin", "本轮开始", payload);
    }

    public void log(String event, Map<String, Object> payload) {
        log(event, messageFor(event), payload);
    }

    public void log(String event, String message, Map<String, Object> payload) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", OffsetDateTime.now().toString());
        record.put("source", "server");
        record.put("event", event);
        record.put("message", message);
        record.put("requestId", mdcValue("requestId"));
        record.put("sessionId", mdcValue("sessionId"));
        record.put("traceId", mdcValue("traceId"));
        record.putAll(payload);
        try {
            timingLog.info(objectMapper.writeValueAsString(record));
        } catch (JsonProcessingException error) {
            timingLog.info("{\"event\":\"{}\",\"serializationError\":\"{}\"}", event, error.getMessage());
        }
    }

    public void log(String event, long totalMs, List<Map<String, Object>> steps, Map<String, Object> payload) {
        Map<String, Object> record = new LinkedHashMap<>(payload);
        record.put("totalMs", totalMs);
        record.put("steps", steps);
        log(event, messageFor(event), record);
    }

    private String mdcValue(String key) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? "-" : value;
    }

    private String messageFor(String event) {
        return switch (event) {
            case "conversation_begin" -> "会话开始";
            case "turn_begin" -> "本轮开始";
            case "turn_end", "conversation_turn" -> "本轮结束";
            case "audio_upload" -> "音频上传完成";
            case "audio_transcribe" -> "音频转写完成";
            default -> event;
        };
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }
}
