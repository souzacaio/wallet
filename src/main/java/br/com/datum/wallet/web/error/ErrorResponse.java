package br.com.datum.wallet.web.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        int status,
        Instant timestamp,
        String path,
        List<FieldError> errors
) {
    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, int status, String path) {
        return new ErrorResponse(code, message, status, Instant.now(), path, null);
    }

    public static ErrorResponse of(String code, String message, int status, String path, List<FieldError> errors) {
        return new ErrorResponse(code, message, status, Instant.now(), path, errors);
    }
}
