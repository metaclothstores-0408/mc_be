package com.metacloth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

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

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> other(Exception e, HttpServletRequest r) {
        return ResponseEntity.internalServerError().body(new Error(OffsetDateTime.now(), 500, "An unexpected error occurred.", r.getRequestURI()));
    }

    record Error(OffsetDateTime timestamp, int status, String message, String path) {
    }
}
