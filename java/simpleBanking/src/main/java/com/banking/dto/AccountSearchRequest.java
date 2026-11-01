package com.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for account search.
 *
 * Legacy mapping: replaces the WS-UserInput (60-char alphanumeric) and the
 * subsequent PARSE-USER-INPUT UNSTRING step. Instead of accepting a single
 * space-delimited string and splitting it at runtime, the API receives
 * firstName and lastName as discrete, validated fields. This eliminates the
 * ambiguity of the COBOL UNSTRING approach (e.g. names containing spaces)
 * and enforces input constraints at the API boundary via Bean Validation.
 */
public record AccountSearchRequest(

        /**
         * Customer first name.
         * Corresponds to WS-Input-FirstName (PIC X(30)) after PARSE-USER-INPUT.
         */
        @NotBlank(message = "First name must not be blank")
        @Size(max = 30, message = "First name must not exceed 30 characters")
        String firstName,

        /**
         * Customer last name.
         * Corresponds to WS-Input-LastName (PIC X(30)) after PARSE-USER-INPUT.
         */
        @NotBlank(message = "Last name must not be blank")
        @Size(max = 30, message = "Last name must not exceed 30 characters")
        String lastName
) {}
