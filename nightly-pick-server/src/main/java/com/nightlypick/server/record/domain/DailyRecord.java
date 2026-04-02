package com.nightlypick.server.record.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyRecord(
        String id,
        String userId,
        String sessionId,
        LocalDate date,
        String title,
        String summary,
        List<String> events,
        List<String> emotions,
        List<String> openLoops,
        String highlight,
        OffsetDateTime createdAt
) {
}
