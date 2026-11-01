package com.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO carrying the user-entered full name string.
 *
 * Design note: The legacy COBOL program accepted a single combined string
 * (WS-UserInput, PIC X(60)) and split it on a space delimiter via UNSTRING.
 * Rather than recreating that screen-level acceptance at the API boundary,
 * we accept the same combined string here and enforce via Bean Validation
 * that it contains at least one space separating first name from last name.
 * This mirrors the COBOL precondition: "WS-UserInput must contain at least
 * one space delimiter before PARSE-USER-INPUT is invoked."
 *
 * The exact case-sensitive comparison requirement from the Data Quality
 * Requirement is enforced in AccountService; no normalisation is applied here.
 */
public class AccountLookupRequest {

    /**
     * The full name as entered by the user, e.g. "John Smith".
     * Must be non-blank and must contain at least one space so that
     * both a first-name token and a last-name token can be extracted,
     * matching the COBOL UNSTRING precondition.
     *
     * The pattern allows any non-whitespace characters on both sides
     * of one or more spaces, preserving the original 60-character
     * field constraint as a reasonable maximum length.
     */
    @NotBlank(message = "Full name must not be blank")
    @Pattern(
            regexp = "^\\S+\\s+\\S.*$",
            message = "Full name must contain at least one space between first name and last name"
    )
    private String fullName;

    // Default constructor required by Jackson deserialisation
    public AccountLookupRequest() {
    }

    public AccountLookupRequest(String fullName) {
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @Override
    public String toString() {
        return "AccountLookupRequest{fullName='" + fullName + "'}";
    }
}
