package com.banking.controller;

import com.banking.dto.AccountLookupRequest;
import com.banking.dto.AccountLookupResponse;
import com.banking.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller replacing the legacy COBOL MAIN-PROCEDURE console interaction.
 *
 * The original COBOL program used DISPLAY/ACCEPT statements for console I/O and
 * a sequential file scan loop to find an account. This controller exposes that
 * same use case as a single POST endpoint, decoupling the I/O boundary from the
 * business logic now encapsulated in AccountService.
 *
 * Resource-oriented path: /api/v1/accounts/lookup
 * HTTP POST is used because the client submits a name to be resolved; GET is
 * inappropriate here since the request carries a body with user-supplied input.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    // Constructor injection - the only injection style used in this project
    public AccountController(final AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Look up an account by the customer's full name.
     *
     * Legacy mapping:
     *   DISPLAY prompt          -> client sends POST request
     *   ACCEPT WS-UserInput     -> @RequestBody AccountLookupRequest
     *   PARSE-USER-INPUT        -> delegated to AccountService
     *   file scan + IF match    -> delegated to AccountService
     *   DISPLAY balance / error -> AccountLookupResponse returned to caller
     *
     * Returns 200 OK with the matched balance, or 404 Not Found when no
     * matching account exists (see GlobalExceptionHandler for 404 mapping).
     *
     * @param request validated request DTO containing the full name string
     * @return ResponseEntity wrapping the lookup result
     */
    @PostMapping("/lookup")
    public ResponseEntity<AccountLookupResponse> lookupAccount(
            @RequestBody @Valid final AccountLookupRequest request) {

        final AccountLookupResponse response = accountService.lookupAccount(request);
        return ResponseEntity.ok(response);
    }
}
