package com.banking.repository;

import com.banking.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for Account entities.
 *
 * Legacy mapping note: The original COBOL program performed a sequential scan
 * of a flat file (accounts.txt), reading every record until a first/last name
 * match was found (PERFORM UNTIL … AT END). That linear search is replaced here
 * by a targeted JPQL-backed query. The exact-match semantics (WS-Input-FirstName
 * = WS-FirstName AND WS-Input-LastName = WS-LastName) are preserved through the
 * derived query method name, which generates a WHERE firstName = ? AND lastName = ?
 * clause — functionally identical to the COBOL EVALUATE condition.
 *
 * Returning Optional<Account> replaces the Name-Match flag ('Y'/'N') and the
 * HIGH-VALUES sentinel used to short-circuit the legacy loop: an empty Optional
 * signals "no match" without requiring a mutable flag or a file-position trick.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Looks up an account by exact first and last name.
     *
     * Replaces the sequential file read loop in MAIN-PROCEDURE combined with
     * the PARSE-ACCOUNT-LINE unstring logic. The database engine performs the
     * comparison that the COBOL program did record-by-record in working storage.
     *
     * @param firstName the customer's first name (maps to WS-Input-FirstName)
     * @param lastName  the customer's last name  (maps to WS-Input-LastName)
     * @return an Optional containing the matched Account, or empty if none found
     */
    Optional<Account> findByFirstNameAndLastName(String firstName, String lastName);
}
