package com.nightlypick.server.record.api;

import com.nightlypick.server.agent.dto.AgentGenerateShareCardRequest;
import com.nightlypick.server.agent.dto.AgentGenerateShareCardResponse;
import com.nightlypick.server.agent.service.AgentClient;
import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.DailyRecordStore;
import com.nightlypick.server.record.application.ShareCardRateLimitService;
import com.nightlypick.server.record.domain.DailyRecord;
import com.nightlypick.server.user.application.UserContext;
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
    private final UserContext userContext;
    private final ShareCardRateLimitService shareCardRateLimitService;

    public RecordController(
            DailyRecordStore dailyRecordStore,
            AgentClient agentClient,
            UserContext userContext,
            ShareCardRateLimitService shareCardRateLimitService
    ) {
        this.dailyRecordStore = dailyRecordStore;
        this.agentClient = agentClient;
        this.userContext = userContext;
        this.shareCardRateLimitService = shareCardRateLimitService;
    }

    @GetMapping
    public ApiResponse<List<DailyRecord>> listRecords() {
        return ApiResponse.ok(dailyRecordStore.listRecords(userContext.getCurrentUserId()));
    }

    @GetMapping("/{recordId}")
    public ApiResponse<DailyRecord> getRecord(@PathVariable String recordId) {
        return ApiResponse.ok(dailyRecordStore.getRecordForUser(recordId, userContext.getCurrentUserId()));
    }

    @PostMapping("/{recordId}/share-card")
    public ApiResponse<GenerateShareCardResponse> generateShareCard(
            @PathVariable String recordId,
            @RequestBody GenerateShareCardRequest request
    ) {
        DailyRecord record = dailyRecordStore.getRecordForUser(recordId, userContext.getCurrentUserId());
        shareCardRateLimitService.checkAndMark(userContext.getCurrentUserId(), record.id());
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
        return ApiResponse.ok(dailyRecordStore.updateRecordForUser(recordId, userContext.getCurrentUserId(), request.title(), request.summary()));
    }

    @DeleteMapping("/{recordId}")
    public ApiResponse<Boolean> deleteRecord(@PathVariable String recordId) {
        dailyRecordStore.deleteRecordForUser(recordId, userContext.getCurrentUserId());
        return ApiResponse.ok(true);
    }
}
