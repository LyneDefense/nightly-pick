package com.nightlypick.server.audio.api;

import com.nightlypick.server.agent.dto.AgentTranscribeAudioRequest;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audio")
public class AudioController {

    private final AgentClient agentClient;

    public AudioController(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    @PostMapping("/upload")
    public ApiResponse<AudioUploadResponse> upload(@RequestBody AudioUploadRequest request) {
        String audioUrl = "https://example.com/uploaded/" + request.fileName();
        return ApiResponse.ok(new AudioUploadResponse(audioUrl));
    }

    @PostMapping("/transcribe")
    public ApiResponse<AudioTranscribeResponse> transcribe(@RequestBody AudioUploadRequest request) {
        var response = agentClient.transcribeAudio(new AgentTranscribeAudioRequest(request.sessionId(), "https://example.com/uploaded/" + request.fileName()));
        return ApiResponse.ok(new AudioTranscribeResponse(response.transcriptText()));
    }
}
