package com.banking.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler providing uniform, RFC 7807 Problem Detail responses
 * for all error scenarios surfaced by the banking account lookup service.
 *
 * Design note: The legacy COBOL program had no structured error handling —
 * an inaccessible accounts.txt would cause an abnormal termination (ABEND).
 * The operational requirement mandates that the modernised system surfaces
 * user-intelligible errors instead. This @ControllerAdvice fulfils that mandate
 * by catching all known exception types and mapping them to well-formed HTTP
 * responses with descriptive detail messages.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final URI VALIDATION_ERROR_TYPE =
            URI.create("https://banking.com/errors/validation-error");
    private static final URI NOT_FOUND_TYPE =
            URI.create("https://banking.com/errors/account-not-found");
    private static final URI FILE_ACCESS_TYPE =
            URI.create("https://banking.com/errors/file-access-error");
    private static final URI INTERNAL_ERROR_TYPE =
            URI.create("https://banking.com/errors/internal-error");

    /**
     * Handles Bean Validation failures on @RequestBody arguments annotated with @Valid.
     *
     * Maps to the legacy precondition check: WS-UserInput must contain at least one
     * space delimiter between first and last name before UNSTRING can succeed.
     * Rather than silently producing an empty last name (as the COBOL program did),
     * the REST layer rejects the request early with a descriptive 400 response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(VALIDATION_ERROR_TYPE);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more request fields failed validation.");
        problem.setProperty("timestamp", Instant.now().toString());

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handles constraint violations triggered on method parameters or path variables
     * when @Validated is used at the class or method level.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(VALIDATION_ERROR_TYPE);
        problem.setTitle("Constraint Violation");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handles the case where no account matches the supplied first and last name.
     *
     * Directly corresponds to the COBOL outcome: after the PERFORM UNTIL loop exits
     * and Name-Match = 'N', the program DISPLAYed "Kein Konto für <WS-UserInput> gefunden."
     * The modernised system instead returns a structured 404 response containing
     * the original user input so callers can display a meaningful not-found message.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAccountNotFound(
            AccountNotFoundException ex) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(NOT_FOUND_TYPE);
        problem.setTitle("Account Not Found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * Handles file I/O errors that originate in AccountFileLoader during application startup.
     *
     * The legacy COBOL program would ABEND if accounts.txt was absent or inaccessible
     * at OPEN INPUT time because there was no error-handling path (FILE STATUS not evaluated).
     * The operational requirement mandates a user-intelligible error in this scenario.
     * AccountFileLoader wraps such failures in an AccountFileAccessException so they can
     * be surfaced here with a clear 503 Service Unavailable response.
     */
    @ExceptionHandler(AccountFileNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAccountFileAccess(
            AccountNotFoundException ex) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(FILE_ACCESS_TYPE);
        problem.setTitle("Account File Access Error");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    /**
     * Catch-all handler for any unexpected runtime exceptions not covered by the
     * more specific handlers above. Returns a generic 500 Internal Server Error
     * without leaking internal stack trace details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(INTERNAL_ERROR_TYPE);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please contact support.");
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
