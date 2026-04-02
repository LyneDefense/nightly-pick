package com.nightlypick.server.audio.api;

import com.nightlypick.server.audio.application.AudioStorageService;
import com.nightlypick.server.agent.dto.AgentTranscribeAudioRequest;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.common.api.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/audio")
public class AudioController {

    private final AgentClient agentClient;
    private final AudioStorageService audioStorageService;

    public AudioController(AgentClient agentClient, AudioStorageService audioStorageService) {
        this.agentClient = agentClient;
        this.audioStorageService = audioStorageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AudioUploadResponse> upload(
            @RequestParam String sessionId,
            @RequestParam("file") MultipartFile file
    ) {
        String audioUrl = audioStorageService.store(sessionId, file);
        return ApiResponse.ok(new AudioUploadResponse(audioUrl));
    }

    @PostMapping("/transcribe")
    public ApiResponse<AudioTranscribeResponse> transcribe(@RequestBody AudioTranscribeRequest request) {
        var response = agentClient.transcribeAudio(new AgentTranscribeAudioRequest(request.sessionId(), request.audioUrl()));
        return ApiResponse.ok(new AudioTranscribeResponse(response.transcriptText()));
    }
}
