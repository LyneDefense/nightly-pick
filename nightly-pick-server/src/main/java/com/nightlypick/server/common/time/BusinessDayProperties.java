package com.nightlypick.server.common.time;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "business-day")
public class BusinessDayProperties {
    private int resetHour = 6;

    public int getResetHour() {
        return resetHour;
    }

    public void setResetHour(int resetHour) {
        this.resetHour = resetHour;
    }
}
