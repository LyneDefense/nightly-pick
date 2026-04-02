package com.nightlypick.server.memory.domain;

import java.time.OffsetDateTime;

public record MemoryItem(
        String id,
        String userId,
        String type,
        String content,
        String topicKey,
        int mentionCount,
        double importanceScore,
        String sourceRecordId,
        OffsetDateTime createdAt,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt
) {
}
