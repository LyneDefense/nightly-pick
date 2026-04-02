package com.nightlypick.server.record.api;

import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.DailyRecordStore;
import com.nightlypick.server.record.domain.DailyRecord;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/records")
public class RecordController {

    private final DailyRecordStore dailyRecordStore;

    public RecordController(DailyRecordStore dailyRecordStore) {
        this.dailyRecordStore = dailyRecordStore;
    }

    @GetMapping
    public ApiResponse<List<DailyRecord>> listRecords() {
        return ApiResponse.ok(dailyRecordStore.listRecords());
    }

    @GetMapping("/{recordId}")
    public ApiResponse<DailyRecord> getRecord(@PathVariable String recordId) {
        return ApiResponse.ok(dailyRecordStore.getRecord(recordId));
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
