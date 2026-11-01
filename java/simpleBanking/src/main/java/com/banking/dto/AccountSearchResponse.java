package com.banking.dto;

import java.math.BigDecimal;

/**
 * Response DTO for account search results.
 *
 * Legacy mapping: In the original COBOL MAIN-PROCEDURE, a successful match triggered
 * DISPLAY of WS-FirstName, WS-LastName, and WS-Balance to the operator console.
 * A failed match triggered a German-language not-found message including WS-UserInput.
 *
 * This DTO replaces both console outputs with a structured, machine-readable response
 * that carries either the matched account details or a descriptive not-found message.
 *
 * WS-Balance was stored as alphanumeric (PIC X(20)) in COBOL; it is mapped here to
 * BigDecimal to enforce correct monetary semantics in the modern system.
 */
public class AccountSearchResponse {

    /**
     * Indicates whether a matching account was found.
     * Maps to the COBOL Name-Match flag ('Y'/'N').
     */
    private final boolean found;

    /**
     * First name of the matched customer. Null when no match was found.
     * Maps to WS-FirstName after PARSE-ACCOUNT-LINE processing.
     */
    private final String firstName;

    /**
     * Last name of the matched customer. Null when no match was found.
     * Maps to WS-LastName after PARSE-ACCOUNT-LINE processing.
     */
    private final String lastName;

    /**
     * Account balance of the matched customer. Null when no match was found.
     * Maps to WS-Balance (originally PIC X(20)); promoted to BigDecimal for
     * correct monetary representation.
     */
    private final BigDecimal balance;

    /**
     * Human-readable message describing the search outcome.
     * On success: confirmation message with customer name.
     * On failure: mirrors the COBOL message 'Kein Konto für [input] gefunden.'
     */
    private final String message;

    private AccountSearchResponse(
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

    /**
     * Factory method for a successful account lookup.
     *
     * @param firstName matched customer first name
     * @param lastName  matched customer last name
     * @param balance   matched account balance
     * @return a response representing a found account
     */
    public static AccountSearchResponse found(String firstName, String lastName, BigDecimal balance) {
        String message = String.format("Der Kontostand von %s %s lautet: %s", firstName, lastName, balance.toPlainString());
        return new AccountSearchResponse(true, firstName, lastName, balance, message);
    }

    /**
     * Factory method for a failed account lookup.
     * Mirrors the COBOL logic: IF Name-Match = "N" DISPLAY "Kein Konto für " WS-UserInput " gefunden."
     *
     * @param userInput the original search input provided by the operator (maps to WS-UserInput)
     * @return a response representing a not-found result
     */
    public static AccountSearchResponse notFound(String userInput) {
        String message = String.format("Kein Konto für %s gefunden.", userInput);
        return new AccountSearchResponse(false, null, null, null, message);
    }

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
}
