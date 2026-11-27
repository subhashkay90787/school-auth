package com.school.auth.controller;

import com.school.auth.otp.OtpRateLimitedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Database rules (entitlement ceiling, same-organization keys, branch limit ...) come back as 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root.getMessage() == null ? "Request conflicts with existing data." : root.getMessage();
        // Postgres messages: first line only, without "ERROR: " and the Where/Detail tail
        message = message.lines().findFirst().orElse(message).replaceFirst("^ERROR:\\s*", "");
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleForbidden(SecurityException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(OtpRateLimitedException.class)
    public ProblemDetail handleRateLimited(OtpRateLimitedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }
}
