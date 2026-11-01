package com.banking.service;

import com.banking.domain.Account;
import com.banking.dto.AccountLookupRequest;
import com.banking.dto.AccountLookupResponse;
import com.banking.exception.AccountNotFoundException;
import com.banking.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AccountService}.
 *
 * The legacy COBOL program performed:
 *  1. UNSTRING WS-UserInput BY SPACE -> first/last name tokens
 *  2. Sequential file scan with exact, case-sensitive IF comparison
 *  3. DISPLAY balance or not-found message
 *
 * These tests verify that the equivalent Java service logic handles all
 * relevant scenarios correctly without depending on the file system or a
 * real database (Mockito is used to stub AccountRepository).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account sampleAccount;

    @BeforeEach
    void setUp() {
        // Represents a CSV record: John,Doe,1990-01-15,1234.56
        sampleAccount = new Account();
        sampleAccount.setId(Long.valueOf(1L));
        sampleAccount.setFirstName("John");
        sampleAccount.setLastName("Doe");
        sampleAccount.setBirthDate(LocalDate.of(1990, 1, 15));
        sampleAccount.setBalance(new BigDecimal("1234.56"));
    }

    // -----------------------------------------------------------------------
    // Happy-path: matching account found
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("lookupAccount: returns balance when first and last name match exactly (case-sensitive)")
    void lookupAccount_matchFound_returnsBalance() {
        // Arrange
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("John Doe");

        when(accountRepository.findFirstByFirstNameAndLastName("John", "Doe"))
                .thenReturn(Optional.of(sampleAccount));

        // Act
        AccountLookupResponse response = accountService.lookupAccount(request);

        // Assert
        assertThat(response.isFound()).isTrue();
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(response.getMessage()).isEqualTo("Der Kontostand von John Doe lautet: 1234.56");

        verify(accountRepository).findFirstByFirstNameAndLastName("John", "Doe");
    }

    @Test
    @DisplayName("lookupAccount: handles extra whitespace tokens — only first two tokens used")
    void lookupAccount_extraWhitespace_usesFirstTwoTokens() {
        // Arrange – user typed two spaces between names; split should still work
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("John  Doe");

        when(accountRepository.findFirstByFirstNameAndLastName("John", "Doe"))
                .thenReturn(Optional.of(sampleAccount));

        // Act
        AccountLookupResponse response = accountService.lookupAccount(request);

        // Assert
        assertThat(response.isFound()).isTrue();
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    // -----------------------------------------------------------------------
    // Not-found: no matching account
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("lookupAccount: throws AccountNotFoundException when no record matches")
    void lookupAccount_noMatch_throwsAccountNotFoundException() {
        // Arrange
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("Jane Smith");

        when(accountRepository.findFirstByFirstNameAndLastName("Jane", "Smith"))
                .thenReturn(Optional.empty());

        // Act & Assert
        // The COBOL program displayed "Kein Konto für <input> gefunden."
        // The modern equivalent throws a domain exception that the
        // GlobalExceptionHandler converts to a 404 response.
        assertThatThrownBy(() -> accountService.lookupAccount(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Jane Smith");

        verify(accountRepository).findFirstByFirstNameAndLastName("Jane", "Smith");
    }

    // -----------------------------------------------------------------------
    // Case-sensitivity: exact match required (legacy behaviour preserved)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("lookupAccount: case-sensitive — 'john doe' does NOT match 'John Doe'")
    void lookupAccount_wrongCase_throwsAccountNotFoundException() {
        // Arrange
        // The legacy COBOL IF comparison applied no INSPECT/UPPER transformation;
        // 'john doe' must NOT match 'John Doe'.
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("john doe");

        when(accountRepository.findFirstByFirstNameAndLastName("john", "doe"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.lookupAccount(request))
                .isInstanceOf(AccountNotFoundException.class);

        // Verify repository was queried with the un-normalised tokens
        verify(accountRepository).findFirstByFirstNameAndLastName("john", "doe");
        verify(accountRepository, never()).findFirstByFirstNameAndLastName("John", "Doe");
    }

    // -----------------------------------------------------------------------
    // Input validation: missing last name (no space in input)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("lookupAccount: throws IllegalArgumentException when fullName contains no space")
    void lookupAccount_noSpaceInInput_throwsIllegalArgumentException() {
        // Arrange
        // The legacy COBOL UNSTRING would leave WS-Input-LastName empty, causing
        // no match. The modern service validates eagerly and fails fast.
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("JohnDoe");

        // Act & Assert
        assertThatThrownBy(() -> accountService.lookupAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("space");

        // Repository must not be queried when input is structurally invalid
        verify(accountRepository, never()).findFirstByFirstNameAndLastName(anyString(), anyString());
    }

    @Test
    @DisplayName("lookupAccount: throws IllegalArgumentException when fullName is blank")
    void lookupAccount_blankInput_throwsIllegalArgumentException() {
        // Arrange
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("   ");

        // Act & Assert
        assertThatThrownBy(() -> accountService.lookupAccount(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accountRepository, never()).findFirstByFirstNameAndLastName(anyString(), anyString());
    }

    @Test
    @DisplayName("lookupAccount: throws IllegalArgumentException when fullName is null")
    void lookupAccount_nullInput_throwsIllegalArgumentException() {
        // Arrange
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName(null);

        // Act & Assert
        assertThatThrownBy(() -> accountService.lookupAccount(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accountRepository, never()).findFirstByFirstNameAndLastName(anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // Edge case: only first name supplied (trailing space)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("lookupAccount: throws IllegalArgumentException when last name token is empty after split")
    void lookupAccount_missingLastNameToken_throwsIllegalArgumentException() {
        // Arrange – user typed 'John ' (trailing space, no last name)
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("John ");

        // Act & Assert
        assertThatThrownBy(() -> accountService.lookupAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last name");

        verify(accountRepository, never()).findFirstByFirstNameAndLastName(anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // Verify repository interaction count (first match stops the scan)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("lookupAccount: repository is queried exactly once per request")
    void lookupAccount_repositoryCalledExactlyOnce() {
        // In legacy COBOL the loop exited early after the first match
        // (MOVE HIGH-VALUES TO Line-End). The repository lookup naturally
        // mirrors this: one findFirstByFirstNameAndLastName call per request.
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("John Doe");

        when(accountRepository.findFirstByFirstNameAndLastName("John", "Doe"))
                .thenReturn(Optional.of(sampleAccount));

        accountService.lookupAccount(request);

        verify(accountRepository, times(1)).findFirstByFirstNameAndLastName("John", "Doe");
        verifyNoMoreInteractions(accountRepository);
    }
}
