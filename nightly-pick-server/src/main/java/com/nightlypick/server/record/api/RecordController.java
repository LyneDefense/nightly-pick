package com.nightlypick.server.record.api;

import com.nightlypick.server.agent.dto.AgentGenerateShareCardRequest;
import com.nightlypick.server.agent.dto.AgentGenerateShareCardResponse;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.DailyRecordStore;
import com.nightlypick.server.record.domain.DailyRecord;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/records")
public class RecordController {

    private final DailyRecordStore dailyRecordStore;
    private final AgentClient agentClient;

    public RecordController(DailyRecordStore dailyRecordStore, AgentClient agentClient) {
        this.dailyRecordStore = dailyRecordStore;
        this.agentClient = agentClient;
    }

    @GetMapping
    public ApiResponse<List<DailyRecord>> listRecords() {
        return ApiResponse.ok(dailyRecordStore.listRecords());
    }

    @GetMapping("/{recordId}")
    public ApiResponse<DailyRecord> getRecord(@PathVariable String recordId) {
        return ApiResponse.ok(dailyRecordStore.getRecord(recordId));
    }

    @PostMapping("/{recordId}/share-card")
    public ApiResponse<GenerateShareCardResponse> generateShareCard(
            @PathVariable String recordId,
            @RequestBody GenerateShareCardRequest request
    ) {
        DailyRecord record = dailyRecordStore.getRecord(recordId);
        String cardType = request == null || request.cardType() == null || request.cardType().isBlank()
                ? "today"
                : request.cardType();
        AgentGenerateShareCardResponse response = agentClient.generateShareCard(
                new AgentGenerateShareCardRequest(
                        record.id(),
                        cardType,
                        record.date() == null ? "" : record.date().toString(),
                        record.title(),
                        record.summary(),
                        record.highlight(),
                        record.events(),
                        record.emotions(),
                        record.openLoops()
                )
        );
        return ApiResponse.ok(new GenerateShareCardResponse(cardType, response.headline(), response.subline()));
    }

    @PatchMapping("/{recordId}")
    public ApiResponse<DailyRecord> updateRecord(
            @PathVariable String recordId,
            @RequestBody UpdateRecordRequest request
    ) {
        return ApiResponse.ok(dailyRecordStore.updateRecord(recordId, request.title(), request.summary()));
    }

    @DeleteMapping("/{recordId}")
    public ApiResponse<Boolean> deleteRecord(@PathVariable String recordId) {
        dailyRecordStore.deleteRecord(recordId);
        return ApiResponse.ok(true);
    }
}
