package com.nightlypick.server.record.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShareCardRateLimitService {

    private final Map<String, ShareCardGenerationState> generationStateByKey = new ConcurrentHashMap<>();

    public void checkAndMark(String userId, String recordId) {
        String normalizedUserId = userId == null ? "" : userId.trim();
        String normalizedRecordId = recordId == null ? "" : recordId.trim();
        String key = normalizedUserId + "::" + normalizedRecordId;
        Instant now = Instant.now();
        ShareCardGenerationState currentState = generationStateByKey.get(key);

        if (currentState != null && currentState.lastGeneratedAt() != null) {
            long elapsedSeconds = Duration.between(currentState.lastGeneratedAt(), now).getSeconds();
            long requiredInterval = requiredIntervalSecondsForNextGeneration(currentState.generationCount());
            if (elapsedSeconds < requiredInterval) {
                long retryAfterSeconds = Math.max(requiredInterval - elapsedSeconds, 1);
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "生成过于频繁，请 " + retryAfterSeconds + " 秒后再试"
                );
            }
        }

        int nextGenerationCount = currentState == null ? 1 : currentState.generationCount() + 1;
        generationStateByKey.put(key, new ShareCardGenerationState(nextGenerationCount, now));
    }

    private long requiredIntervalSecondsForNextGeneration(int generationCount) {
        if (generationCount <= 0) {
            return 0;
        }
        if (generationCount == 1) {
            return 5;
        }
        if (generationCount == 2) {
            return 10;
        }
        if (generationCount == 3) {
            return 30;
        }
        return 60;
    }

    private record ShareCardGenerationState(
            int generationCount,
            Instant lastGeneratedAt
    ) {
    }
}
