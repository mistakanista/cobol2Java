package com.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Banking Account Lookup application.
 *
 * Legacy context: The original COBOL program (BankingApp) executed as a
 * single MAIN-PROCEDURE performing sequential file reads and console I/O.
 * This Spring Boot application replaces that monolithic procedural flow with
 * a layered, service-oriented REST backend. Console I/O is replaced by a
 * REST API; sequential file reading at startup is handled by AccountFileLoader;
 * and all business logic is encapsulated in AccountService.
 */
@SpringBootApplication
public class BankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
