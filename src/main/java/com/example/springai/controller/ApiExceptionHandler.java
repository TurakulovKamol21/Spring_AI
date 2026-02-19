package com.example.springai.controller;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(
                new ApiError(
                        "REQUEST_ERROR",
                        safeMessage(ex.getReason(), "Request xatoligi"),
                        "So'rov parametrlarini tekshiring.",
                        Instant.now(),
                        request.getRequestURI()
                )
        );
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<ApiError> handleNonTransientAiException(NonTransientAiException ex, HttpServletRequest request) {
        String message = safeMessage(ex.getMessage(), "AI provider xatoligi");
        String low = message.toLowerCase();

        if (isInsufficientQuota(low)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    new ApiError(
                            "INSUFFICIENT_QUOTA",
                            "OpenAI quota tugagan. Billing va plan holatini tekshiring.",
                            "https://platform.openai.com/settings/organization/billing",
                            Instant.now(),
                            request.getRequestURI()
                    )
            );
        }

        if (isAuthError(low)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new ApiError(
                            "OPENAI_AUTH_ERROR",
                            "OpenAI API key noto'g'ri yoki ruxsat yo'q.",
                            "OPENAI_API_KEY ni tekshiring.",
                            Instant.now(),
                            request.getRequestURI()
                    )
            );
        }

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new ApiError(
                        "AI_PROVIDER_ERROR",
                        "AI provider javobida xatolik: " + shortMessage(message),
                        "Model/API sozlamalarini tekshirib qayta urinib ko'ring.",
                        Instant.now(),
                        request.getRequestURI()
                )
        );
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleWebClientException(WebClientResponseException ex, HttpServletRequest request) {
        String message = safeMessage(ex.getResponseBodyAsString(), ex.getMessage());
        String low = message.toLowerCase();

        if (ex.getStatusCode().value() == 429 && isInsufficientQuota(low)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    new ApiError(
                            "INSUFFICIENT_QUOTA",
                            "OpenAI quota tugagan. Billing va plan holatini tekshiring.",
                            "https://platform.openai.com/settings/organization/billing",
                            Instant.now(),
                            request.getRequestURI()
                    )
            );
        }

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new ApiError(
                        "UPSTREAM_HTTP_ERROR",
                        "Upstream xizmat xatosi: HTTP " + ex.getStatusCode().value(),
                        "Keyinroq qayta urinib ko'ring.",
                        Instant.now(),
                        request.getRequestURI()
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiError(
                        "INTERNAL_ERROR",
                        "Kutilmagan server xatoligi",
                        "Loglarni tekshirib qayta urinib ko'ring.",
                        Instant.now(),
                        request.getRequestURI()
                )
        );
    }

    private boolean isInsufficientQuota(String message) {
        return message.contains("insufficient_quota") || message.contains("current quota");
    }

    private boolean isAuthError(String message) {
        return message.contains("invalid_api_key")
                || message.contains("incorrect api key")
                || message.contains("unauthorized");
    }

    private String safeMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String shortMessage(String message) {
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }

    public record ApiError(
            String code,
            String message,
            String action,
            Instant timestamp,
            String path
    ) {
    }
}
