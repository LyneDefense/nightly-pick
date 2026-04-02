package com.nightlypick.server.settings.api;

import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.ConversationSessionStore;
import com.nightlypick.server.conversation.application.store.MemoryStore;
import com.nightlypick.server.conversation.application.store.UserProfileStore;
import com.nightlypick.server.user.application.UserContext;
import com.nightlypick.server.user.domain.UserProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/me")
public class SettingsController {

    private final UserProfileStore userProfileStore;
    private final MemoryStore memoryStore;
    private final ConversationSessionStore conversationSessionStore;
    private final UserContext userContext;

    public SettingsController(
            UserProfileStore userProfileStore,
            MemoryStore memoryStore,
            ConversationSessionStore conversationSessionStore,
            UserContext userContext
    ) {
        this.userProfileStore = userProfileStore;
        this.memoryStore = memoryStore;
        this.conversationSessionStore = conversationSessionStore;
        this.userContext = userContext;
    }

    @GetMapping("/settings")
    public ApiResponse<UserProfile> getSettings() {
        return ApiResponse.ok(userProfileStore.getUser(userContext.getCurrentUserId()));
    }

    @PatchMapping("/settings")
    public ApiResponse<UserProfile> updateSettings(@RequestBody UpdateSettingsRequest request) {
        boolean allow = request.allowMemoryReference() == null || request.allowMemoryReference();
        return ApiResponse.ok(userProfileStore.updateUserSettings(userContext.getCurrentUserId(), allow));
    }

    @PostMapping("/clear-memories")
    public ApiResponse<Map<String, Object>> clearMemories() {
        memoryStore.clearMemories(userContext.getCurrentUserId());
        return ApiResponse.ok(Map.of("cleared", true));
    }

    @PostMapping("/clear-history")
    public ApiResponse<Map<String, Object>> clearHistory() {
        conversationSessionStore.clearHistory(userContext.getCurrentUserId());
        return ApiResponse.ok(Map.of("cleared", true));
    }
}
