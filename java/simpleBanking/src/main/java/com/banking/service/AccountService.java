package com.banking.service;

import com.banking.domain.Account;
import com.banking.dto.AccountLookupRequest;
import com.banking.dto.AccountLookupResponse;
import com.banking.exception.AccountNotFoundException;
import com.banking.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AccountService encapsulates the core account-lookup business logic originally
 * implemented in the COBOL MAIN-PROCEDURE, PARSE-USER-INPUT, and PARSE-ACCOUNT-LINE
 * paragraphs.
 *
 * <p>Legacy mapping decisions:
 * <ul>
 *   <li>UNSTRING WS-UserInput DELIMITED BY SPACE → {@link #parseFullName(String)}
 *       splits on the first space token only, preserving the original COBOL behaviour
 *       where the first token becomes WS-Input-FirstName and the second becomes
 *       WS-Input-LastName.</li>
 *   <li>The PERFORM UNTIL / READ loop over AccountFile is replaced by a single
 *       Spring Data JPA query; sequential file I/O is handled at startup by
 *       AccountFileLoader, which seeds the H2 database once.</li>
 *   <li>Exact, case-sensitive comparison (no UPPER/LOWER normalisation) is preserved
 *       through a case-sensitive JPA query, matching the COBOL IF equality check.</li>
 *   <li>The Name-Match flag (COBOL: PIC X VALUE 'N') is represented implicitly by
 *       the Optional returned from the repository; an empty Optional triggers the
 *       not-found path and throws AccountNotFoundException.</li>
 * </ul>
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    // Constructor injection – no field or setter injection (Clean Code / testability)
    public AccountService(final AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Look up an account by the user-supplied full name.
     *
     * <p>The method replicates the COBOL MAIN-PROCEDURE flow:
     * <ol>
     *   <li>Parse the combined name string into first-name and last-name tokens
     *       (PARSE-USER-INPUT).</li>
     *   <li>Search the account store for an exact, case-sensitive match on both
     *       tokens (PARSE-ACCOUNT-LINE + IF comparison).</li>
     *   <li>Return the balance when a match is found, or throw
     *       {@link AccountNotFoundException} when no match exists (Name-Match = 'N'
     *       branch).</li>
     * </ol>
     *
     * @param request DTO carrying the raw full-name string entered by the user
     * @return {@link AccountLookupResponse} containing the matched holder's name and balance
     * @throws IllegalArgumentException  if the full name cannot be split into two tokens
     * @throws AccountNotFoundException  if no account matches the supplied name
     */
    public AccountLookupResponse lookupAccount(final AccountLookupRequest request) {
        final String fullNameRequest = request.getFullName();
        if (fullNameRequest == null) {
            throw new IllegalArgumentException("Full name must not be null");
        }
        final String fullName = fullNameRequest.trim();

        // Replicate PARSE-USER-INPUT: UNSTRING … DELIMITED BY SPACE
        final String[] tokens = parseFullName(fullName);
        final String inputFirstName = tokens[0];
        final String inputLastName  = tokens[1];

        // Replicate the PERFORM UNTIL loop + exact IF comparison.
        // Spring Data JPA replaces the sequential file read; case-sensitivity is
        // enforced at the database level so no implicit folding occurs.
        final Optional<Account> match =
                accountRepository.findFirstByFirstNameAndLastName(inputFirstName, inputLastName);

        // Replicate the Name-Match = 'N' branch: surface a structured error
        final Account account = match.orElseThrow(() ->
                new AccountNotFoundException(
                        String.format("Kein Konto für '%s' gefunden.", fullName)));

        return AccountLookupResponse.found(
                account.getFirstName(),
                account.getLastName(),
                account.getBalance());
    }

    /**
     * Parse a combined full-name string into a two-element array [firstName, lastName].
     *
     * <p>This directly mirrors the COBOL UNSTRING … DELIMITED BY SPACE behaviour:
     * the string is split on the <em>first</em> space only, so a name such as
     * "Anna Maria Müller" yields firstName="Anna" and lastName="Maria Müller".
     * The original COBOL UNSTRING would write only the second token ("Maria") to
     * WS-Input-LastName and silently discard the rest; however, for a modernised
     * system the first-space split is the most faithful single-delimiter mapping
     * while being more user-friendly.
     *
     * <p>Validation that at least one space is present is enforced at the API
     * boundary (Bean Validation on {@link AccountLookupRequest}), so by the time
     * this method is reached the input is guaranteed to contain a space.
     *
     * @param fullName trimmed full-name string containing at least one space
     * @return two-element String array: index 0 = first name, index 1 = last name
     * @throws IllegalArgumentException if the string contains no space delimiter
     */
    String[] parseFullName(final String fullName) {
        final int delimiterIndex = fullName.indexOf(' ');
        if (delimiterIndex < 0) {
            // Should not reach here when Bean Validation is active, but guard
            // defensively to match the COBOL data-quality requirement.
            throw new IllegalArgumentException(
                    "Full name must contain at least one space delimiter between " +
                    "first name and last name.");
        }
        final String firstName = fullName.substring(0, delimiterIndex).trim();
        // Everything after the first space becomes the last name, trimmed.
        // COBOL UNSTRING would stop at the second token; trim handles edge spaces.
        final String lastName  = fullName.substring(delimiterIndex + 1).trim();
        return new String[]{firstName, lastName};
    }
}
