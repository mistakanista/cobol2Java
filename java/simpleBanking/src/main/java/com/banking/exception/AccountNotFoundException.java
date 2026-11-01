package com.banking.exception;

/**
 * Custom exception thrown when no account record matches the provided first and last name.
 *
 * Legacy mapping: In the original COBOL MAIN-PROCEDURE, after the sequential file search loop
 * completed, the program checked whether Name-Match still equalled 'N' and displayed a
 * German-language not-found message before executing STOP RUN. In the modern architecture this
 * exceptional path is represented as a typed exception so that the GlobalExceptionHandler can
 * produce a consistent, structured HTTP 404 response without polluting service logic with
 * response-building concerns.
 */
public class AccountNotFoundException extends RuntimeException {

    private final String searchedFirstName;
    private final String searchedLastName;

    public AccountNotFoundException(String searchedFirstName, String searchedLastName) {
        super(String.format(
                "Kein Konto für %s %s gefunden.",
                searchedFirstName,
                searchedLastName));
        this.searchedFirstName = searchedFirstName;
        this.searchedLastName = searchedLastName;
    }

    /**
     * Returns the first name that was used in the failed search.
     * Preserves the WS-Input-FirstName value from the original COBOL working-storage.
     *
     * @return searched first name
     */
    public String getSearchedFirstName() {
        return searchedFirstName;
    }

    /**
     * Returns the last name that was used in the failed search.
     * Preserves the WS-Input-LastName value from the original COBOL working-storage.
     *
     * @return searched last name
     */
    public String getSearchedLastName() {
        return searchedLastName;
    }
}
