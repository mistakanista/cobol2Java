package com.banking.controller;

import com.banking.dto.AccountLookupRequest;
import com.banking.dto.AccountLookupResponse;
import com.banking.exception.AccountNotFoundException;
import com.banking.exception.GlobalExceptionHandler;
import com.banking.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for AccountController.
 *
 * Uses @WebMvcTest to load only the web layer; AccountService is mocked via
 * @MockBean so these tests verify HTTP contract (status codes, JSON structure,
 * validation enforcement) without touching the database or file system.
 *
 * Legacy traceability:
 *   - Happy-path test mirrors the COBOL branch where Name-Match = "Y" and
 *     the balance is DISPLAYed.
 *   - Not-found test mirrors the COBOL branch where Name-Match = "N" and the
 *     not-found message is DISPLAYed.
 *   - Validation tests enforce the modernised requirement that the input must
 *     contain at least one space (first-name / last-name separator), replacing
 *     the silent UNSTRING behaviour that left WS-Input-LastName empty.
 */
@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    // ------------------------------------------------------------------
    // Happy-path: account found
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 200 with balance when account is found")
    void lookupAccount_found_returns200WithBalance() throws Exception {
        // Arrange
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("John Smith");

        AccountLookupResponse response = AccountLookupResponse.found(
                "John", "Smith", new BigDecimal("1234.56"));

        when(accountService.lookupAccount(any(AccountLookupRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.found").value(Optional.of(true)))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.balance").value(Optional.of(1234.56)));
    }

    // ------------------------------------------------------------------
    // Not-found path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 404 when no matching account exists")
    void lookupAccount_notFound_returns404() throws Exception {
        // Arrange
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("Jane Doe");

        when(accountService.lookupAccount(any(AccountLookupRequest.class)))
                .thenThrow(new AccountNotFoundException("Jane Doe"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists());
    }

    // ------------------------------------------------------------------
    // Validation: missing fullName field
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 400 when fullName is null")
    void lookupAccount_nullFullName_returns400() throws Exception {
        // Arrange – fullName intentionally omitted
        String body = "{}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errors").exists());
    }

    // ------------------------------------------------------------------
    // Validation: fullName is blank
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 400 when fullName is blank")
    void lookupAccount_blankFullName_returns400() throws Exception {
        // Arrange
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("   ");

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ------------------------------------------------------------------
    // Validation: fullName contains no space (no last-name separator)
    // Legacy COBOL silently left WS-Input-LastName empty in this case;
    // the modernised system rejects the input at the API boundary.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 400 when fullName has no space delimiter")
    void lookupAccount_noSpaceInFullName_returns400() throws Exception {
        // Arrange – single token, no space between first and last name
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("JohnSmith");

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ------------------------------------------------------------------
    // Validation: wrong HTTP content-type
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 415 for non-JSON content type")
    void lookupAccount_wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("John Smith"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ------------------------------------------------------------------
    // Validation: fullName contains only spaces
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 400 when fullName contains only spaces")
    void lookupAccount_onlySpaces_returns400() throws Exception {
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName("     ");

        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ------------------------------------------------------------------
    // Validation: fullName exceeds maximum length (60 chars from COBOL PIC X(60))
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/lookup returns 400 when fullName exceeds 60 characters")
    void lookupAccount_fullNameTooLong_returns400() throws Exception {
        // 61-character string with a space to satisfy the space-presence check
        String longName = "A".repeat(30) + " " + "B".repeat(30); // 61 chars total
        AccountLookupRequest request = new AccountLookupRequest();
        request.setFullName(longName);

        mockMvc.perform(post("/api/v1/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
