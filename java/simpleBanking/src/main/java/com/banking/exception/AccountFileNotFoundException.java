package com.banking.exception;

import lombok.Getter;

/**
 * Custom runtime exception thrown by AccountService when no account record
 * matches the supplied first name and last name combination.
 *
 * Replaces the legacy COBOL behaviour where the Name-Match flag remained 'N'
 * after the full sequential file scan and a not-found DISPLAY was emitted.
 * Throwing a dedicated exception here keeps the service return type clean
 * (always a valid AccountLookupResponse on success) and delegates error
 * response formatting to the GlobalExceptionHandler.
 */
@Getter
public class AccountFileNotFoundException extends RuntimeException {

    /**
     * -- GETTER --
     *  Returns the original full-name string entered by the user, mirroring
     *  the legacy DISPLAY that included WS-UserInput in the not-found message.
     *
     * @return the raw user-supplied name query
     */
    private final String queriedFullName;

    public AccountFileNotFoundException(String queriedFullName) {
        super("No account found for: " + queriedFullName);
        this.queriedFullName = queriedFullName;
    }

}
