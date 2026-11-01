package com.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handling translated from the legacy COBOL program's
 * inline error checks (e.g. IF Name-Match = "N" ... DISPLAY "Kein Konto für...").
 *
 * In the original COBOL, error conditions were handled procedurally with DISPLAY
 * statements directly in MAIN-PROCEDURE. Here we separate those concerns cleanly:
 * business exceptions bubble up through the service layer and are mapped to
 * appropriate HTTP responses in this single, authoritative handler.
 *
 * Using ProblemDetail (RFC 9457 / RFC 7807) provides a standardised, machine-readable
 * error format suitable for REST APIs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI ACCOUNT_NOT_FOUND_TYPE =
            URI.create("https://banking.com/errors/account-not-found");

    private static final URI VALIDATION_ERROR_TYPE =
            URI.create("https://banking.com/errors/validation-error");

    private static final URI INTERNAL_ERROR_TYPE =
            URI.create("https://banking.com/errors/internal-server-error");

    /**
     * Handles the case where no matching account record was found.
     *
     * Maps directly from the legacy logic:
     *   IF Name-Match = "N"
     *     DISPLAY "Kein Konto für " WS-UserInput " gefunden."
     *   END-IF
     *
     * Instead of a DISPLAY statement, we return a structured 404 response.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAccountNotFoundException(
            AccountNotFoundException ex) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Account Not Found");
        problemDetail.setType(ACCOUNT_NOT_FOUND_TYPE);
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Handles Bean Validation failures on incoming request DTOs.
     *
     * In the legacy COBOL, user input was accepted raw from the console (ACCEPT WS-UserInput)
     * with no structured validation. Here we validate at the API boundary using
     * @Valid and @NotBlank annotations on the request DTO, returning field-level
     * error details to the caller.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Invalid value",
                        // Merge duplicate field keys by concatenating messages
                        (existing, replacement) -> existing + "; " + replacement
                ));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields failed validation."
        );
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(VALIDATION_ERROR_TYPE);
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Catch-all handler for any unexpected runtime exceptions.
     *
     * The legacy COBOL program had no general error handling mechanism;
     * any unexpected condition would result in an abnormal program termination (ABEND).
     * This handler prevents unhandled exceptions from leaking stack traces to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support."
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(INTERNAL_ERROR_TYPE);
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problemDetail);
    }
}
