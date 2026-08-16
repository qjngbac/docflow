package com.docflow.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Conflict"),
    PAYLOAD_TOO_LARGE(413, "Payload too large"),
    TOO_MANY_REQUESTS(429, "Too many requests"),
    SERVICE_UNAVAILABLE(503, "Service unavailable"),
    INTERNAL_ERROR(500, "Internal server error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
