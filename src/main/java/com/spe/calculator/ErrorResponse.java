package com.spe.calculator;

import java.time.Instant;

public record ErrorResponse(int status, String error, String message, Instant timestamp) {

    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(400, "Bad Request", message, Instant.now());
    }
}
