package com.nightlypick.server.auth.api;

import com.nightlypick.server.common.api.ApiResponse;
import com.nightlypick.server.conversation.application.store.UserProfileStore;
import com.nightlypick.server.user.domain.UserProfile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserProfileStore userProfileStore;

    public AuthController(UserProfileStore userProfileStore) {
        this.userProfileStore = userProfileStore;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody(required = false) LoginRequest request) {
        String nickname = request == null ? null : request.nickname();
        UserProfile user = userProfileStore.login(nickname);
        return ApiResponse.ok(new LoginResponse("demo-token", user));
    }
}
