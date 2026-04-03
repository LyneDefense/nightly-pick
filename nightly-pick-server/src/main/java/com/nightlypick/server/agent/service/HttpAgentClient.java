package com.nightlypick.server.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.nightlypick.server.agent.config.AgentProperties;
import com.nightlypick.server.agent.dto.AgentChatReplyRequest;
import com.nightlypick.server.agent.dto.AgentChatReplyResponse;
import com.nightlypick.server.agent.dto.AgentExtractMemoryRequest;
import com.nightlypick.server.agent.dto.AgentExtractMemoryResponse;
import com.nightlypick.server.agent.dto.AgentGenerateRecordRequest;
import com.nightlypick.server.agent.dto.AgentGenerateRecordResponse;
import com.nightlypick.server.agent.dto.AgentGenerateShareCardRequest;
import com.nightlypick.server.agent.dto.AgentGenerateShareCardResponse;
import com.nightlypick.server.agent.dto.AgentPlanReflectionRequest;
import com.nightlypick.server.agent.dto.AgentPlanReflectionResponse;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechRequest;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechResponse;
import com.nightlypick.server.agent.dto.AgentTranscribeAudioRequest;
import com.nightlypick.server.agent.dto.AgentTranscribeAudioResponse;
import com.nightlypick.server.agent.dto.AgentWriteReflectionRequest;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.Objects;

@Component
public class HttpAgentClient implements AgentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAgentClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper agentObjectMapper;
    private final AgentProperties agentProperties;

    public HttpAgentClient(ObjectMapper objectMapper, AgentProperties agentProperties) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.agentObjectMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.agentProperties = agentProperties;
    }

    @Override
    public AgentChatReplyResponse getChatReply(AgentChatReplyRequest request) {
        return post("/chat/reply", request, AgentChatReplyResponse.class);
    }

    @Override
    public AgentGenerateRecordResponse generateRecord(AgentGenerateRecordRequest request) {
        return post("/record/generate", request, AgentGenerateRecordResponse.class);
    }

    @Override
    public AgentGenerateShareCardResponse generateShareCard(AgentGenerateShareCardRequest request) {
        return post("/record/share-card", request, AgentGenerateShareCardResponse.class);
    }

    @Override
    public AgentPlanReflectionResponse planReflection(AgentPlanReflectionRequest request) {
        return post("/record/plan", request, AgentPlanReflectionResponse.class);
    }

    @Override
    public AgentGenerateRecordResponse writeReflection(AgentWriteReflectionRequest request) {
        return post("/record/write", request, AgentGenerateRecordResponse.class);
    }

    @Override
    public AgentTranscribeAudioResponse transcribeAudio(AgentTranscribeAudioRequest request) {
        return post("/speech/transcribe", request, AgentTranscribeAudioResponse.class);
    }

    @Override
    public AgentSynthesizeSpeechResponse synthesizeSpeech(AgentSynthesizeSpeechRequest request) {
        return post("/speech/synthesize", request, AgentSynthesizeSpeechResponse.class);
    }

    @Override
    public AgentExtractMemoryResponse extractMemory(AgentExtractMemoryRequest request) {
        return post("/memory/extract", request, AgentExtractMemoryResponse.class);
    }

    private <T> T post(String path, Object requestBody, Class<T> responseType) {
        try {
            String body = agentObjectMapper.writeValueAsString(requestBody);
            long startedAt = System.currentTimeMillis();
            String requestId = Objects.requireNonNullElse(MDC.get("requestId"), UUID.randomUUID().toString());
            String sessionId = extractSessionId(requestBody);
            log.info("开始请求 Agent 服务 requestId={} sessionId={} path={} body={}", requestId, sessionId, path, body);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(agentProperties.baseUrl() + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("X-Request-Id", requestId)
                    .header("X-Session-Id", sessionId == null ? "-" : sessionId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("Agent 服务响应完成 requestId={} sessionId={} path={} status={} elapsedMs={} body={}",
                    requestId,
                    sessionId,
                    path,
                    response.statusCode(),
                    elapsedMs,
                    response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Agent call failed: " + response.statusCode() + " " + response.body()
                );
            }
            return agentObjectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize agent request.", e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            log.error("请求 Agent 服务时发生 IO 异常", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call agent service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("请求 Agent 服务时线程被中断", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent call was interrupted.", e);
        }
    }

    private String extractSessionId(Object requestBody) {
        if (requestBody instanceof AgentChatReplyRequest request) return request.sessionId();
        if (requestBody instanceof AgentGenerateRecordRequest request) return request.sessionId();
        if (requestBody instanceof AgentGenerateShareCardRequest request) return request.recordId();
        if (requestBody instanceof AgentPlanReflectionRequest request) return request.sessionId();
        if (requestBody instanceof AgentWriteReflectionRequest request) return request.sessionId();
        if (requestBody instanceof AgentTranscribeAudioRequest request) return request.sessionId();
        return null;
    }
}
