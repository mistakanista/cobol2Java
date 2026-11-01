package com.banking.controller;

import com.banking.domain.Account;
import com.banking.dto.AccountSearchRequest;
import com.banking.dto.AccountSearchResponse;
import com.banking.exception.AccountNotFoundException;
import com.banking.exception.GlobalExceptionHandler;
import com.banking.repository.AccountRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring MVC slice tests for {@link AccountController}.
 *
 * Uses @WebMvcTest to load only the web layer, isolating the controller from the
 * service and repository layers. The AccountService is replaced by a Mockito mock
 * so that each test controls exactly what the service returns, mirroring the
 * legacy program's two terminal states: match-found and not-found.
 */
@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // AccountService is mocked to keep tests pure web-layer tests
    @MockBean
    private AccountService accountService;

    @MockBean
    private AccountRepository accountRepository;

    // ---------------------------------------------------------------------------
    // Happy-path: account found
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 200 with balance when account exists")
    void searchAccount_found_returns200WithBalance() throws Exception {
        // Arrange
        AccountSearchRequest request = new AccountSearchRequest("Max", "Mustermann");

        AccountSearchResponse response = AccountSearchResponse.found(
                "Max",
                "Mustermann",
                new BigDecimal("1500.75")
        );
        Account sampleAccount = new Account("Max", "Mustermann", "1990-01-01", new BigDecimal("1500.75"));

        when(accountRepository.findByFirstNameAndLastName("Max", "Mustermann")).thenReturn(Optional.of(sampleAccount));
        when(accountService.findAccount(any(AccountSearchRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.firstName").value("Max"))
                .andExpect(jsonPath("$.lastName").value("Mustermann"))
                .andExpect(jsonPath("$.balance").value(1500.75));

        verify(accountService).findAccount(any(AccountSearchRequest.class));
    }

    // ---------------------------------------------------------------------------
    // Not-found path: service throws AccountNotFoundException
    // Mirrors the legacy COBOL branch: IF Name-Match = "N" -> display not-found
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 404 when account does not exist")
    void searchAccount_notFound_returns404() throws Exception {
        // Arrange
        AccountSearchRequest request = new AccountSearchRequest("Hans", "Meier");

        when(accountService.findAccount(any(AccountSearchRequest.class)))
                .thenThrow(new AccountNotFoundException("Hans", "Meier"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    // ---------------------------------------------------------------------------
    // Bean Validation: missing firstName
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 400 when firstName is blank")
    void searchAccount_blankFirstName_returns400() throws Exception {
        // Arrange – firstName left blank to trigger @NotBlank validation
        AccountSearchRequest request = new AccountSearchRequest("", "Mustermann");

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Bean Validation: missing lastName
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 400 when lastName is blank")
    void searchAccount_blankLastName_returns400() throws Exception {
        // Arrange – lastName left blank to trigger @NotBlank validation
        AccountSearchRequest request = new AccountSearchRequest("Max", "");

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Bean Validation: both fields missing
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 400 when both name fields are null")
    void searchAccount_nullFields_returns400() throws Exception {
        // Arrange – both fields null to trigger @NotBlank on both
        AccountSearchRequest request = new AccountSearchRequest(null, null);

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Content-type guard: non-JSON body
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 415 when Content-Type is not JSON")
    void searchAccount_wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Max Mustermann"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------------------------------------------------------------------------
    // Malformed JSON body
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 400 when request body is malformed JSON")
    void searchAccount_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Whitespace-only names (equivalent to the legacy UNSTRING producing empty tokens)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/search returns 400 when firstName is whitespace only")
    void searchAccount_whitespaceFirstName_returns400() throws Exception {
        AccountSearchRequest request = new AccountSearchRequest("   ", "Mustermann");

        mockMvc.perform(post("/api/v1/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
