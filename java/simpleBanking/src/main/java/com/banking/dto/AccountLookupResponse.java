package com.banking.dto;

import java.math.BigDecimal;

/**
 * Response DTO for an account lookup operation.
 *
 * Design note: The original COBOL program had two mutually exclusive console
 * outputs – either a balance display or a not-found message. Rather than
 * modelling these as two separate response types, a single DTO with an
 * explicit 'found' flag is used so that callers can branch on one field
 * without inspecting nullable values. The balance is typed as BigDecimal
 * (mapped from the COBOL PIC X(20) WS-Balance field) to support precise
 * monetary arithmetic in downstream processing.
 */
public class AccountLookupResponse {

    /**
     * Indicates whether a matching account was located.
     * Mirrors the COBOL Name-Match flag (initialised 'N', set to 'Y' on match).
     */
    private final boolean found;

    /** First name of the matched account holder; null when not found. */
    private final String firstName;

    /** Last name of the matched account holder; null when not found. */
    private final String lastName;

    /**
     * Account balance of the matched record; null when not found.
     * BigDecimal is used instead of the raw COBOL PIC X(20) string to
     * guarantee correct decimal semantics for money values.
     */
    private final BigDecimal balance;

    /**
     * Human-readable message. Contains the not-found notification
     * (including the original user input) when found == false, or a
     * confirmation message when found == true.
     */
    private final String message;

    // -----------------------------------------------------------------------
    // Private all-args constructor – use static factory methods below
    // -----------------------------------------------------------------------

    private AccountLookupResponse(
            boolean found,
            String firstName,
            String lastName,
            BigDecimal balance,
            String message) {
        this.found = found;
        this.firstName = firstName;
        this.lastName = lastName;
        this.balance = balance;
        this.message = message;
    }

    // -----------------------------------------------------------------------
    // Static factory methods
    // -----------------------------------------------------------------------

    /**
     * Creates a successful lookup response.
     *
     * <p>Equivalent to the COBOL branch:
     * {@code DISPLAY "Der Kontostand von " WS-FirstName SPACE WS-LastName " lautet: " WS-Balance}
     *
     * @param firstName matched account first name
     * @param lastName  matched account last name
     * @param balance   matched account balance
     * @return populated response with found == true
     */
    public static AccountLookupResponse found(String firstName, String lastName, BigDecimal balance) {
        String message = String.format(
                "Der Kontostand von %s %s lautet: %s", firstName, lastName, balance.toPlainString());
        return new AccountLookupResponse(true, firstName, lastName, balance, message);
    }

    /**
     * Creates a not-found lookup response.
     *
     * <p>Equivalent to the COBOL branch:
     * {@code DISPLAY "Kein Konto für " WS-UserInput " gefunden."}
     *
     * @param originalUserInput the raw name string entered by the user
     * @return populated response with found == false
     */
    public static AccountLookupResponse notFound(String originalUserInput) {
        String message = String.format("Kein Konto für %s gefunden.", originalUserInput);
        return new AccountLookupResponse(false, null, null, null, message);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public boolean isFound() {
        return found;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "AccountLookupResponse{"
                + "found=" + found
                + ", firstName='" + firstName + '\''
                + ", lastName='" + lastName + '\''
                + ", balance=" + balance
                + ", message='" + message + '\''
                + '}';
    }
}
