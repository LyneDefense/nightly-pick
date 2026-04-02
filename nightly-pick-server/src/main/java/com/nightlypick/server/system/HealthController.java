package com.nightlypick.server.system;

import com.nightlypick.server.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "service", "nightly-pick-server",
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString()
        ));
    }
}
