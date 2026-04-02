package com.nightlypick.server.common.time;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Component
public class BusinessDayClock {
    private final BusinessDayProperties businessDayProperties;

    public BusinessDayClock(BusinessDayProperties businessDayProperties) {
        this.businessDayProperties = businessDayProperties;
    }

    public LocalDate currentBusinessDate() {
        return toBusinessDate(OffsetDateTime.now());
    }

    public LocalDate toBusinessDate(OffsetDateTime value) {
        if (value == null) {
            return currentBusinessDate();
        }
        LocalDate date = value.toLocalDate();
        if (value.toLocalTime().isBefore(LocalTime.of(normalizedResetHour(), 0))) {
            return date.minusDays(1);
        }
        return date;
    }

    public int resetHour() {
        return normalizedResetHour();
    }

    private int normalizedResetHour() {
        int resetHour = businessDayProperties.getResetHour();
        if (resetHour < 0) return 0;
        if (resetHour > 23) return 23;
        return resetHour;
    }
}
