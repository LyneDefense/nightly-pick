package com.nightlypick.server.agent.service;

import com.nightlypick.server.agent.dto.AgentChatReplyRequest;
import com.nightlypick.server.agent.dto.AgentChatReplyResponse;
import com.nightlypick.server.agent.dto.AgentGenerateRecordRequest;
import com.nightlypick.server.agent.dto.AgentGenerateRecordResponse;
import com.nightlypick.server.agent.dto.AgentPlanReflectionRequest;
import com.nightlypick.server.agent.dto.AgentPlanReflectionResponse;
import com.nightlypick.server.agent.dto.AgentWriteReflectionRequest;
import com.nightlypick.server.agent.dto.AgentExtractMemoryRequest;
import com.nightlypick.server.agent.dto.AgentExtractMemoryResponse;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechRequest;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechResponse;
import com.nightlypick.server.agent.dto.AgentTranscribeAudioRequest;
import com.nightlypick.server.agent.dto.AgentTranscribeAudioResponse;

public interface AgentClient {

    AgentChatReplyResponse getChatReply(AgentChatReplyRequest request);

    AgentGenerateRecordResponse generateRecord(AgentGenerateRecordRequest request);

    AgentPlanReflectionResponse planReflection(AgentPlanReflectionRequest request);

    AgentGenerateRecordResponse writeReflection(AgentWriteReflectionRequest request);

    AgentExtractMemoryResponse extractMemory(AgentExtractMemoryRequest request);

    AgentTranscribeAudioResponse transcribeAudio(AgentTranscribeAudioRequest request);

    AgentSynthesizeSpeechResponse synthesizeSpeech(AgentSynthesizeSpeechRequest request);
}
