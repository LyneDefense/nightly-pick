package com.nightlypick.server.audio.api;

import com.nightlypick.server.audio.application.AudioStorageService;
import com.nightlypick.server.agent.dto.AgentTranscribeAudioRequest;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.common.timing.TimingLogService;
import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import com.nightlypick.server.conversation.domain.ConversationSession;
import com.nightlypick.server.common.time.BusinessDayClock;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audio")
public class AudioController {

    private final AgentClient agentClient;
    private final AudioStorageService audioStorageService;
    private final ConversationSessionStore conversationSessionStore;
    private final BusinessDayClock businessDayClock;
    private final TimingLogService timingLogService;

    public AudioController(
            AgentClient agentClient,
            AudioStorageService audioStorageService,
            ConversationSessionStore conversationSessionStore,
            BusinessDayClock businessDayClock,
            TimingLogService timingLogService
    ) {
        this.agentClient = agentClient;
        this.audioStorageService = audioStorageService;
        this.conversationSessionStore = conversationSessionStore;
        this.businessDayClock = businessDayClock;
        this.timingLogService = timingLogService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AudioUploadResponse> upload(
            @RequestParam String sessionId,
            @RequestParam("file") MultipartFile file
    ) {
        long startedAt = System.nanoTime();
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        String audioUrl = null;
        String status = "ok";
        String errorMessage = null;
        String businessDate = resolveBusinessDate(sessionId);
        try {
            MDC.put("businessDate", businessDate);
            audioUrl = audioStorageService.store(sessionId, file);
            steps.add(timingLogService.step("store_file", "保存录音文件", elapsedMs(startedAt)));
            return ApiResponse.ok(new AudioUploadResponse(audioUrl));
        } catch (RuntimeException error) {
            status = "error";
            errorMessage = error.getMessage();
            throw error;
        } finally {
            payload.put("sessionId", sessionId);
            payload.put("businessDate", businessDate);
            payload.put("fileSize", file == null ? 0 : file.getSize());
            payload.put("audioUrl", audioUrl);
            payload.put("status", status);
            payload.put("errorMessage", errorMessage);
            timingLogService.log("audio_upload", elapsedMs(startedAt), steps, payload);
            MDC.remove("businessDate");
        }
    }

    @PostMapping("/transcribe")
    public ApiResponse<AudioTranscribeResponse> transcribe(@RequestBody AudioTranscribeRequest request) {
        long startedAt = System.nanoTime();
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        String transcriptText = null;
        String status = "ok";
        String errorMessage = null;
        String businessDate = resolveBusinessDate(request.sessionId());
        try {
            MDC.put("businessDate", businessDate);
            long agentStartedAt = System.nanoTime();
            var response = agentClient.transcribeAudio(new AgentTranscribeAudioRequest(request.sessionId(), request.audioUrl()));
            steps.add(timingLogService.step("agent_transcribe", "请求 Agent 转写", elapsedMs(agentStartedAt)));
            transcriptText = response.transcriptText();
            return ApiResponse.ok(new AudioTranscribeResponse(transcriptText));
        } catch (RuntimeException error) {
            status = "error";
            errorMessage = error.getMessage();
            throw error;
        } finally {
            payload.put("sessionId", request.sessionId());
            payload.put("businessDate", businessDate);
            payload.put("audioUrl", request.audioUrl());
            payload.put("transcriptLength", transcriptText == null ? 0 : transcriptText.length());
            payload.put("status", status);
            payload.put("errorMessage", errorMessage);
            timingLogService.log("audio_transcribe", elapsedMs(startedAt), steps, payload);
            MDC.remove("businessDate");
        }
    }

    private long elapsedMs(long startedAtNanos) {
        return Math.max(0L, Math.round((System.nanoTime() - startedAtNanos) / 1_000_000.0));
    }

    private String resolveBusinessDate(String sessionId) {
        ConversationSession session = conversationSessionStore.getSession(sessionId);
        return businessDayClock.toBusinessDate(session.startedAt()).toString();
    }
}
