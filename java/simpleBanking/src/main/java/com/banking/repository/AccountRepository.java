package com.banking.repository;

import com.banking.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the Account entity.
 *
 * Design note: The legacy COBOL program performed a full sequential file scan
 * comparing WS-Input-FirstName and WS-Input-LastName against each record using
 * strict AND equality (no case folding, no normalisation). That exact,
 * case-sensitive AND match is preserved here via a derived query method whose
 * generated SQL uses equality predicates on both columns – semantically
 * identical to the COBOL IF WS-Input-FirstName = WS-FirstName AND
 * WS-Input-LastName = WS-LastName condition.
 *
 * Returning Optional<Account> makes the not-found path explicit and removes
 * the need for the legacy Name-Match flag that tracked whether any record was
 * located during the file scan loop.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Finds the first account whose firstName and lastName match the supplied
     * values using exact, case-sensitive equality – mirroring the COBOL
     * comparison logic exactly as mandated by the Data Quality Requirement.
     *
     * @param firstName the first name token parsed from the user-supplied input
     * @param lastName  the last name token parsed from the user-supplied input
     * @return an Optional containing the matched Account, or empty if none found
     */
    Optional<Account> findFirstByFirstNameAndLastName(String firstName, String lastName);
}
