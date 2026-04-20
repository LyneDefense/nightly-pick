package com.nightlypick.server.common.timing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TimingLogService {
    private static final Logger timingLog = LoggerFactory.getLogger("com.nightlypick.server.timing");
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

    public void log(String event, Map<String, Object> payload) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("event", event);
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
        log(event, record);
    }

    private String mdcValue(String key) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? "-" : value;
    }
}
