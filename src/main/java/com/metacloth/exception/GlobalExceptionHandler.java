package com.metacloth.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> api(ApiException e, HttpServletRequest r) {
        return ResponseEntity.status(e.status).body(new Error(OffsetDateTime.now(), e.status.value(), e.getMessage(), r.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Error> val(MethodArgumentNotValidException e, HttpServletRequest r) {
        return ResponseEntity.badRequest().body(new Error(OffsetDateTime.now(), 400, "Invalid request data", r.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Error> maxUpload(MaxUploadSizeExceededException e, HttpServletRequest r) {
        log.warn("Upload size exceeded at {}: {}", r.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new Error(OffsetDateTime.now(), 413, "Uploaded file exceeds maximum permitted size (50MB).", r.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> other(Exception e, HttpServletRequest r) {
        log.error("Unhandled exception at {}: {}", r.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.internalServerError().body(new Error(OffsetDateTime.now(), 500, e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "An unexpected error occurred.", r.getRequestURI()));
    }

    record Error(OffsetDateTime timestamp, int status, String message, String path) {
    }
}
