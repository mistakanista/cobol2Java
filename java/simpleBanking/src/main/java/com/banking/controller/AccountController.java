package com.banking.controller;

import com.banking.dto.AccountSearchRequest;
import com.banking.dto.AccountSearchResponse;
import com.banking.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing account search functionality.
 *
 * Legacy mapping: The original COBOL MAIN-PROCEDURE accepted operator console input
 * (ACCEPT WS-UserInput) and performed a sequential file search. This is translated into
 * a POST endpoint accepting a structured JSON request body, replacing the console I/O
 * and file-read loop with a service-layer database query.
 *
 * Resource path /api/v1/accounts/search uses POST because the operation transmits
 * search criteria in the request body (firstName + lastName), which is not idiomatic
 * for GET with a body. This avoids embedding PII in query parameters or URL paths.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    // Constructor injection only – no field or setter injection (Clean Code / testability)
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Search for an account by first name and last name.
     *
     * Translates the legacy behaviour:
     *   1. DISPLAY prompt + ACCEPT WS-UserInput  →  @RequestBody with @Valid
     *   2. PERFORM PARSE-USER-INPUT (UNSTRING on SPACE)  →  structured DTO fields
     *   3. Sequential file search loop  →  AccountService.findAccount()
     *   4. DISPLAY balance or "Kein Konto gefunden"  →  AccountSearchResponse body
     *
     * HTTP 200  – account found, response contains customer name and balance
     * HTTP 404  – no matching account (AccountNotFoundException → GlobalExceptionHandler)
     * HTTP 400  – invalid/missing request fields (Bean Validation)
     *
     * @param request validated search criteria
     * @return matched account details
     */
    @PostMapping("/search")
    public ResponseEntity<AccountSearchResponse> searchAccount(
            @Valid @RequestBody AccountSearchRequest request) {

        AccountSearchResponse response = accountService.findAccount(request);

        return ResponseEntity.ok(response);
    }
}
