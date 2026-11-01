package com.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Banking Application.
 *
 * Legacy context: The original COBOL program (BankingApp) was a batch/online
 * COBOL program invoked via STOP RUN. This Spring Boot application replaces
 * that execution model with a long-running service exposing REST endpoints,
 * removing the need for operator console interaction and file-based sequential
 * reads in favour of a JPA-backed REST API.
 */
@SpringBootApplication
public class BankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
