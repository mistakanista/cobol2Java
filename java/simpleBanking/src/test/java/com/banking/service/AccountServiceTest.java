package com.banking.service;

import com.banking.domain.Account;
import com.banking.dto.AccountSearchRequest;
import com.banking.dto.AccountSearchResponse;
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
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountService.
 *
 * Legacy mapping note: The original COBOL MAIN-PROCEDURE performed an exact
 * string comparison (WS-Input-FirstName = WS-FirstName AND WS-Input-LastName =
 * WS-LastName) after UNSTRING on SPACE. These tests verify that the service
 * preserves exact-match semantics and correctly translates the Name-Match flag
 * logic (set to 'Y' on hit, stay 'N' on miss) into exception-based flow.
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
        // Represents a single comma-delimited record from the legacy AccountFile:
        // Hans,Mueller,1985-03-12,1234.56
        sampleAccount = new Account();
        sampleAccount.setId(1L);
        sampleAccount.setFirstName("Hans");
        sampleAccount.setLastName("Mueller");
        sampleAccount.setBirthDate(LocalDate.of(1985, Month.MARCH, 12).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        sampleAccount.setBalance(new BigDecimal("1234.56"));
    }

    // -------------------------------------------------------------------------
    // Happy path – account found
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAccount: returns response with balance when exact name match found")
    void findAccount_exactMatch_returnsAccountResponse() {
        // Arrange
        AccountSearchRequest request = new AccountSearchRequest("Hans", "Mueller");
        when(accountRepository.findByFirstNameAndLastName("Hans", "Mueller"))
                .thenReturn(Optional.of(sampleAccount));

        // Act
        AccountSearchResponse response = accountService.findAccount(request);

        // Assert – mirrors DISPLAY "Der Kontostand von " WS-FirstName SPACE WS-LastName
        assertThat(response.isFound()).isTrue();
        assertThat(response.getFirstName()).isEqualTo("Hans");
        assertThat(response.getLastName()).isEqualTo("Mueller");
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(response.getMessage()).contains("Hans").contains("Mueller");

        verify(accountRepository, times(1)).findByFirstNameAndLastName("Hans", "Mueller");
    }

    @Test
    @DisplayName("findAccount: response message contains customer full name on match")
    void findAccount_matchFound_messageContainsFullName() {
        AccountSearchRequest request = new AccountSearchRequest("Hans", "Mueller");
        when(accountRepository.findByFirstNameAndLastName("Hans", "Mueller"))
                .thenReturn(Optional.of(sampleAccount));

        AccountSearchResponse response = accountService.findAccount(request);

        // The legacy program displayed both name parts and the balance in one line.
        assertThat(response.getMessage())
                .contains("Hans")
                .contains("Mueller")
                .contains("1234.56");
    }

    // -------------------------------------------------------------------------
    // Not-found path – mirrors Name-Match = 'N' branch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAccount: throws AccountNotFoundException when no record matches")
    void findAccount_noMatch_throwsAccountNotFoundException() {
        // Arrange – simulates exhausting all AccountFile records without a hit
        AccountSearchRequest request = new AccountSearchRequest("Unknown", "Person");
        when(accountRepository.findByFirstNameAndLastName("Unknown", "Person"))
                .thenReturn(Optional.empty());

        // Act & Assert – mirrors DISPLAY "Kein Konto für " WS-UserInput " gefunden."
        assertThatThrownBy(() -> accountService.findAccount(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Unknown")
                .hasMessageContaining("Person");
    }

    @Test
    @DisplayName("findAccount: exception message includes both first and last name from request")
    void findAccount_noMatch_exceptionMessageContainsInputNames() {
        AccountSearchRequest request = new AccountSearchRequest("Anna", "Schmidt");
        when(accountRepository.findByFirstNameAndLastName("Anna", "Schmidt"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findAccount(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Anna Schmidt");
    }

    // -------------------------------------------------------------------------
    // Input parsing edge cases – mirror UNSTRING on SPACE from PARSE-USER-INPUT
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseUserInput: splits space-delimited string into firstName and lastName")
    void parseUserInput_validInput_returnsParsedRequest() {
        // The COBOL UNSTRING WS-UserInput DELIMITED BY SPACE INTO
        //   WS-Input-FirstName WS-Input-LastName
        // is replicated in the service helper method.
        AccountSearchRequest result = accountService.parseUserInput("Hans Mueller");

        assertThat(result.firstName()).isEqualTo("Hans");
        assertThat(result.lastName()).isEqualTo("Mueller");
    }

    @Test
    @DisplayName("parseUserInput: trims leading and trailing spaces before splitting")
    void parseUserInput_extraWhitespace_trimmedCorrectly() {
        AccountSearchRequest result = accountService.parseUserInput("  Hans   Mueller  ");

        assertThat(result.firstName()).isEqualTo("Hans");
        assertThat(result.lastName()).isEqualTo("Mueller");
    }

    @Test
    @DisplayName("parseUserInput: single token results in IllegalArgumentException for missing last name")
    void parseUserInput_singleToken_lastNameIsEmpty() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.parseUserInput("Hans")
        );

        assertThat(exception.getMessage())
                .isEqualTo("User input must contain both a first name and a last name separated by a space.");
    }

    @Test
    @DisplayName("parseUserInput: empty string throws IllegalArgumentException")
    void parseUserInput_emptyString_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.parseUserInput(""));
    }

    @Test
    @DisplayName("parseUserInput: input with multiple spaces uses only first two tokens and combines the others")
    void parseUserInput_multipleTokens_onlyFirstTwoUsed() {
        // COBOL UNSTRING fills targets left-to-right; any additional tokens are discarded.
        AccountSearchRequest result = accountService.parseUserInput("Hans Otto Mueller");

        assertThat(result.firstName()).isEqualTo("Hans");
        assertThat(result.lastName()).isEqualTo("Otto Mueller");
    }

    // -------------------------------------------------------------------------
    // Exact-match case sensitivity – COBOL comparison is case-sensitive
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAccount: case mismatch does not produce a match (legacy exact comparison)")
    void findAccount_caseMismatch_throwsAccountNotFoundException() {
        // The original COBOL IF comparison is case-sensitive; 'hans' != 'Hans'.
        AccountSearchRequest request = new AccountSearchRequest("hans", "mueller");
        when(accountRepository.findByFirstNameAndLastName("hans", "mueller"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findAccount(request))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository).findByFirstNameAndLastName("hans", "mueller");
    }

    // -------------------------------------------------------------------------
    // Repository interaction verification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAccount: repository is called exactly once per search")
    void findAccount_repositoryCalledOnce() {
        AccountSearchRequest request = new AccountSearchRequest("Hans", "Mueller");
        when(accountRepository.findByFirstNameAndLastName(anyString(), anyString()))
                .thenReturn(Optional.of(sampleAccount));

        accountService.findAccount(request);

        verify(accountRepository, times(1)).findByFirstNameAndLastName("Hans", "Mueller");
        verifyNoMoreInteractions(accountRepository);
    }

    @Test
    @DisplayName("findAccount: balance is returned as BigDecimal preserving full precision")
    void findAccount_balancePrecision_preservedAsBigDecimal() {
        // Legacy WS-Balance was PIC X(20); migrated to BigDecimal to ensure
        // monetary precision is maintained rather than relying on floating-point.
        sampleAccount.setBalance(new BigDecimal("99999999.99"));
        AccountSearchRequest request = new AccountSearchRequest("Hans", "Mueller");
        when(accountRepository.findByFirstNameAndLastName("Hans", "Mueller"))
                .thenReturn(Optional.of(sampleAccount));

        AccountSearchResponse response = accountService.findAccount(request);

        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("99999999.99"));
    }
}
