package com.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * JPA entity representing a single account record.
 *
 * Legacy mapping notes:
 * - The original COBOL program read comma-delimited flat-file records (AccountLine, PIC X(100))
 *   containing four fields: first name, last name, birth date, and balance.
 * - WS-FirstName / WS-LastName (PIC X(30)) map to firstName / lastName String fields.
 * - WS-BirthDate (PIC X(10)) maps to a String field; kept as String to preserve the
 *   original alphanumeric format without imposing a date type contract at this stage.
 * - WS-Balance (PIC X(20)) held a textual balance value; mapped to BigDecimal to enforce
 *   proper monetary arithmetic and eliminate implicit string-to-number conversion risks
 *   that were hidden in the COBOL DISPLAY statement.
 * - Name-Match ('Y'/'N') was a working-storage flag; it is NOT persisted here because it
 *   represents transient search state, now encapsulated in the service layer.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Maps to COBOL WS-FirstName (PIC X(30)).
     * Trimmed on read in the service layer, mirroring COBOL's space-padded field behaviour.
     */
    @Column(name = "first_name", nullable = false, length = 30)
    private String firstName;

    /**
     * Maps to COBOL WS-LastName (PIC X(30)).
     */
    @Column(name = "last_name", nullable = false, length = 30)
    private String lastName;

    /**
     * Maps to COBOL WS-BirthDate (PIC X(10)).
     * Retained as String to avoid imposing a date format at the persistence boundary;
     * the original COBOL field was purely alphanumeric.
     */
    @Column(name = "birth_date", length = 10)
    private String birthDate;

    /**
     * Maps to COBOL WS-Balance (PIC X(20)).
     * Promoted from alphanumeric to BigDecimal to ensure correct monetary representation,
     * avoiding the implicit string comparison the COBOL DISPLAY used.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required by JPA. */
    public Account() {
    }

    public Account(String firstName, String lastName, String birthDate, BigDecimal balance) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.balance = balance;
    }

    // -------------------------------------------------------------------------
    // Getters – no setters exposed publicly; state changes go through the service
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getLastName() {
        return lastName;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "Account{"
                + "id=" + id
                + ", firstName='" + firstName + '\''
                + ", lastName='" + lastName + '\''
                + ", birthDate='" + birthDate + '\''
                + ", balance=" + balance
                + '}';
    }
}
