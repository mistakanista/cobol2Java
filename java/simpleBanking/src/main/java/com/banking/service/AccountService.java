package com.banking.service;

import com.banking.domain.Account;
import com.banking.dto.AccountSearchRequest;
import com.banking.dto.AccountSearchResponse;
import com.banking.exception.AccountNotFoundException;
import com.banking.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * AccountService encapsulates the business logic originally implemented in the
 * COBOL MAIN-PROCEDURE, PARSE-USER-INPUT, and PARSE-ACCOUNT-LINE paragraphs.
 *
 * Legacy mapping notes:
 * - PARSE-USER-INPUT (UNSTRING WS-UserInput DELIMITED BY SPACE) is replaced by
 *   structured DTO input: the caller supplies firstName and lastName directly,
 *   removing the need for console-based string splitting at the service boundary.
 *   Input splitting is still supported via parseUserInput() for programmatic use.
 * - PARSE-ACCOUNT-LINE (UNSTRING AccountLine DELIMITED BY ",") is no longer
 *   needed because account data is stored relationally in the database; the JPA
 *   entity models the individual fields that were previously embedded in a
 *   comma-delimited flat file (AccountFile / accounts.txt).
 * - The PERFORM UNTIL loop over AccountFile records is replaced by a single
 *   Spring Data JPA query (findByFirstNameAndLastName), which is semantically
 *   equivalent to the sequential scan with early-exit on first match.
 * - The Name-Match flag ('Y'/'N') is replaced by Optional<Account> presence,
 *   which is the idiomatic Java equivalent of the 88-level condition name pattern.
 * - HIGH-VALUES loop-termination sentinel is no longer required; the repository
 *   query encapsulates iteration internally.
 * - German-language display messages ("Bitte geben Sie...", "Kein Konto für...",
 *   "Der Kontostand von...") are preserved in the response DTO messages so that
 *   downstream consumers can render them unchanged.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;

    // Constructor injection as required – no field or setter injection.
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Searches for an account by exact first-name and last-name match.
     *
     * Translated from the PERFORM UNTIL loop in MAIN-PROCEDURE:
     *   IF WS-Input-FirstName = WS-FirstName AND WS-Input-LastName = WS-LastName
     *       DISPLAY "Der Kontostand von ..." WS-Balance
     *       MOVE "Y" TO Name-Match
     *   END-IF
     *
     * If no match is found the method throws AccountNotFoundException, which
     * corresponds to the COBOL post-loop decision:
     *   IF Name-Match = "N"
     *       DISPLAY "Kein Konto für " WS-UserInput " gefunden."
     *   END-IF
     *
     * @param request DTO carrying the parsed first and last name tokens
     * @return AccountSearchResponse with matched name and balance
     * @throws AccountNotFoundException when no account matches the supplied name
     */
    public AccountSearchResponse findAccount(AccountSearchRequest request) {
        // Trim whitespace to handle minor input variations; the COBOL source
        // stored values in fixed-length PIC X fields which are space-padded,
        // so trimming is the correct semantic equivalent.
        String firstName = request.firstName().trim();
        String lastName  = request.lastName().trim();

        Optional<Account> match = accountRepository.findByFirstNameAndLastName(firstName, lastName);

        // Name-Match = 'Y' branch: account found, build success response.
        if (match.isPresent()) {
            Account account = match.get();
            return AccountSearchResponse.found(account.getFirstName(), account.getLastName(),
                    account.getBalance());
        }

        // Name-Match = 'N' branch: no record matched, reproduce the German not-found message.
        // Original: DISPLAY "Kein Konto für " WS-UserInput " gefunden."
        // WS-UserInput is reconstructed as "firstName lastName" to mirror the console input.
        throw new AccountNotFoundException(firstName, lastName);
    }

    /**
     * Parses a raw space-delimited user input string into an AccountSearchRequest.
     *
     * This method directly translates the PARSE-USER-INPUT paragraph:
     *   UNSTRING WS-UserInput DELIMITED BY SPACE
     *       INTO WS-Input-FirstName WS-Input-LastName
     *
     * It is exposed as a utility so that callers who receive raw input (e.g.
     * integration tests or CLI adapters) can reproduce the original COBOL
     * UNSTRING behaviour without duplicating the splitting logic.
     *
     * Only the first two space-delimited tokens are used, matching the COBOL
     * UNSTRING INTO clause that targets exactly two receiving fields.
     *
     * @param rawInput the combined "firstName lastName" string (max 60 chars,
     *                 mirroring WS-UserInput PIC X(60))
     * @return AccountSearchRequest populated with the parsed tokens
     * @throws IllegalArgumentException if rawInput is null, blank, or contains
     *                                  fewer than two space-delimited tokens
     */
    public AccountSearchRequest parseUserInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException(
                    "User input must not be blank; expected format: 'firstName lastName'");
        }

        // Mirror the COBOL PIC X(60) field length constraint.
        String trimmedInput = rawInput.trim();
        if (trimmedInput.length() > 60) {
            trimmedInput = trimmedInput.substring(0, 60).trim();
        }

        // UNSTRING ... DELIMITED BY SPACE – split on one or more spaces.
        String[] tokens = trimmedInput.split("\\s+", 2);
        if (tokens.length < 2) {
            throw new IllegalArgumentException(
                    "User input must contain both a first name and a last name separated by a space.");
        }

        // WS-Input-FirstName PIC X(30): cap at 30 characters.
        String firstName = tokens[0].length() > 30 ? tokens[0].substring(0, 30) : tokens[0];
        // WS-Input-LastName PIC X(30): cap at 30 characters.
        String lastName  = tokens[1].length() > 30 ? tokens[1].substring(0, 30) : tokens[1];

        return new AccountSearchRequest(firstName, lastName);
    }
}
