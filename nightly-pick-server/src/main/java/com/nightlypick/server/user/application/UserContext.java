package com.nightlypick.server.user.application;

import org.springframework.stereotype.Component;

@Component
public class UserContext {

    public String getCurrentUserId() {
        return "demo-user";
    }
}
