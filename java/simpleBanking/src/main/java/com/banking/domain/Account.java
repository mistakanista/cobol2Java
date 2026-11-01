package com.banking.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA entity representing a single customer account record.
 *
 * Legacy mapping notes:
 * - Derived from the COBOL FILE SECTION record 'AccountLine' (PIC X(100)) and
 *   the four WORKING-STORAGE fields populated by PARSE-ACCOUNT-LINE:
 *     WS-FirstName  PIC X(30)  -> firstName  (String)
 *     WS-LastName   PIC X(30)  -> lastName   (String)
 *     WS-BirthDate  PIC X(10)  -> birthDate  (LocalDate, ISO-8601 format assumed)
 *     WS-Balance    PIC X(20)  -> balance    (BigDecimal)
 *
 * - WS-Balance was an alphanumeric COBOL field; money amounts are always
 *   represented as BigDecimal in the modernised system to avoid floating-point
 *   rounding errors (see Clean Code / DDD money-value best practice).
 *
 * - birthDate is stored as LocalDate rather than a raw String so that the
 *   domain model is semantically correct, even though the legacy system only
 *   ever displayed it (no arithmetic was performed on it).
 *
 * - Exact, case-sensitive name matching is preserved: no normalisation
 *   (toUpperCase / toLowerCase) is applied, mirroring the legacy IF comparison
 *   with no INSPECT/UPPER conversion in the COBOL source.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    /**
     * Surrogate primary key – the legacy flat file had no concept of a
     * primary key; we introduce one for JPA identity management.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Customer first name (max 30 characters, matching COBOL PIC X(30)).
     * Must not be blank; used in exact case-sensitive lookup.
     */
    @Column(name = "first_name", nullable = false, length = 30)
    private String firstName;

    /**
     * Customer last name (max 30 characters, matching COBOL PIC X(30)).
     * Must not be blank; used in exact case-sensitive lookup.
     */
    @Column(name = "last_name", nullable = false, length = 30)
    private String lastName;

    /**
     * Customer birth date parsed from the third comma-delimited field in
     * accounts.txt (COBOL PIC X(10), ISO-8601 format: YYYY-MM-DD assumed).
     * Nullable to tolerate records where the birth-date field is absent or
     * malformed in the source file.
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Account balance parsed from the fourth comma-delimited field.
     * BigDecimal is mandatory for monetary values to ensure precision.
     * Stored with up to 20 significant digits and 2 decimal places,
     * reflecting the COBOL PIC X(20) source width.
     */
    @Column(name = "balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal balance;
}
