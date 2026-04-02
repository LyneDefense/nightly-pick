package com.nightlypick.server.memory.api;

import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.MemoryStore;
import com.nightlypick.server.memory.domain.MemoryItem;
import com.nightlypick.server.user.application.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/memories")
public class MemoryController {

    private final MemoryStore memoryStore;
    private final UserContext userContext;

    public MemoryController(MemoryStore memoryStore, UserContext userContext) {
        this.memoryStore = memoryStore;
        this.userContext = userContext;
    }

    @GetMapping
    public ApiResponse<List<MemoryItem>> listMemories() {
        return ApiResponse.ok(memoryStore.listMemories(userContext.getCurrentUserId()));
    }
}
