package com.nightlypick.server;

import com.nightlypick.server.agent.dto.AgentChatReplyResponse;
import com.nightlypick.server.agent.dto.AgentExtractMemoryResponse;
import com.nightlypick.server.agent.dto.AgentGenerateRecordResponse;
import com.nightlypick.server.agent.dto.AgentMemoryItemResponse;
import com.nightlypick.server.agent.dto.AgentSynthesizeSpeechResponse;
import com.nightlypick.server.agent.dto.AgentTranscribeAudioResponse;
import com.nightlypick.server.agent.service.AgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentClient agentClient;

    @Test
    void shouldCompleteConversationAndManageRecord() throws Exception {
        given(agentClient.getChatReply(any())).willReturn(new AgentChatReplyResponse(
                "我记下了，今天最值得保留的瞬间是什么？",
                false,
                "exploring",
                "sorting",
                "light_ready"
        ));
        given(agentClient.synthesizeSpeech(any())).willReturn(new AgentSynthesizeSpeechResponse(
                "https://example.com/audio/reply.mp3",
                "Chinese (Mandarin)_Warm_Bestie"
        ));
        given(agentClient.generateRecord(any())).willReturn(new AgentGenerateRecordResponse(
                "今夜记录",
                "今天完成了一次完整复盘。",
                List.of("完成对话"),
                List.of("平静"),
                List.of("还有明天要继续的任务"),
                "愿意停下来回看自己。"
        ));
        given(agentClient.extractMemory(any())).willAnswer(invocation -> {
            var request = invocation.getArgument(0, com.nightlypick.server.agent.dto.AgentExtractMemoryRequest.class);
            return new AgentExtractMemoryResponse(
                    List.of(new AgentMemoryItemResponse("topic", "最近持续提到项目压力", request.recordId()))
            );
        });
        given(agentClient.transcribeAudio(any())).willReturn(new AgentTranscribeAudioResponse("这是示例转写"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"测试用户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.nickname").value("测试用户"));

        String createResponse = mockMvc.perform(post("/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = createResponse.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/conversations/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"今天工作很满，但也推进了一点\",\"inputType\":\"text\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantReply").value("我记下了，今天最值得保留的瞬间是什么？"))
                .andExpect(jsonPath("$.data.assistantAudioUrl").value("https://example.com/audio/reply.mp3"));

        String completeResponse = mockMvc.perform(post("/conversations/" + sessionId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").exists())
                .andExpect(jsonPath("$.data.merged").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String recordId = completeResponse.replaceAll(".*\"recordId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/records/" + recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("今夜记录"));

        mockMvc.perform(patch("/records/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"修改后的标题\",\"summary\":\"修改后的摘要\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("修改后的标题"))
                .andExpect(jsonPath("$.data.summary").value("修改后的摘要"));

        mockMvc.perform(get("/memories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("最近持续提到项目压力"));

        mockMvc.perform(delete("/records/" + recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void shouldKeepOnlyOneDailyRecordAndMergeWhenCompletingMultipleTimesInOneDay() throws Exception {
        given(agentClient.getChatReply(any())).willReturn(new AgentChatReplyResponse(
                "继续说说看。",
                false,
                "exploring",
                "sorting",
                "light_ready"
        ));
        given(agentClient.synthesizeSpeech(any())).willReturn(new AgentSynthesizeSpeechResponse(
                "https://example.com/audio/reply.mp3",
                "Chinese (Mandarin)_Warm_Bestie"
        ));
        given(agentClient.generateRecord(any()))
                .willReturn(new AgentGenerateRecordResponse(
                        "第一次总结",
                        "今天先记录下上午的疲惫。",
                        List.of("上午开会很累"),
                        List.of("疲惫"),
                        List.of("明天继续整理会议纪要"),
                        "先允许自己慢一点。"
                ))
                .willReturn(new AgentGenerateRecordResponse(
                        "第二次总结",
                        "1. 今天先记录下上午的疲惫。\n2. 晚上散步后，心情缓和了一些。",
                        List.of("1. 上午开会很累", "2. 晚上散步后心情缓和"),
                        List.of("疲惫", "平静"),
                        List.of("1. 明天继续整理会议纪要"),
                        "今晚比白天更安稳。"
                ));
        given(agentClient.extractMemory(any())).willAnswer(invocation -> {
            var request = invocation.getArgument(0, com.nightlypick.server.agent.dto.AgentExtractMemoryRequest.class);
            return new AgentExtractMemoryResponse(
                    List.of(new AgentMemoryItemResponse("topic", request.summary(), request.recordId()))
            );
        });

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"测试用户\"}"))
                .andExpect(status().isOk());

        String sessionId1 = mockMvc.perform(post("/conversations"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");

        String firstCompleteResponse = mockMvc.perform(post("/conversations/" + sessionId1 + "/complete"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstRecordId = firstCompleteResponse.replaceAll(".*\"recordId\":\"([^\"]+)\".*", "$1");

        String sessionId2 = mockMvc.perform(post("/conversations"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");

        String secondCompleteResponse = mockMvc.perform(post("/conversations/" + sessionId2 + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merged").value(true))
                .andExpect(jsonPath("$.data.notice").value("已合并到今日总结"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondRecordId = secondCompleteResponse.replaceAll(".*\"recordId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(firstRecordId))
                .andExpect(jsonPath("$.data[0].summary").value("1. 今天先记录下上午的疲惫。\n2. 晚上散步后，心情缓和了一些。"))
                .andExpect(jsonPath("$.data[0].events[0]").value("1. 上午开会很累"))
                .andExpect(jsonPath("$.data[0].events[1]").value("2. 晚上散步后心情缓和"))
                .andExpect(jsonPath("$.data[0].emotions[0]").value("疲惫"))
                .andExpect(jsonPath("$.data[0].emotions[1]").value("平静"));

        org.junit.jupiter.api.Assertions.assertEquals(firstRecordId, secondRecordId);
    }
}
