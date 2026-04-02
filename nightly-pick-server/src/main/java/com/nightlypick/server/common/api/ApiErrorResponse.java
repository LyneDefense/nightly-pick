package com.nightlypick.server.common.api;

public record ApiErrorResponse(
        boolean success,
        String error
) {
    public static ApiErrorResponse of(String error) {
        return new ApiErrorResponse(false, error);
    }
}
