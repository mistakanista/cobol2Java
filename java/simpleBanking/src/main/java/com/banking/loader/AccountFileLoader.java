package com.banking.loader;

import com.banking.domain.Account;
import com.banking.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AccountFileLoader seeds the in-memory H2 repository from the legacy flat file 'accounts.txt'.
 *
 * Legacy mapping rationale:
 * - The COBOL program opened AccountFile (LINE SEQUENTIAL 'accounts.txt') at runtime and read it
 *   record by record. In the modernised architecture there is no runtime flat-file scan; instead
 *   we load all records once at startup into JPA/H2 so that subsequent REST lookups hit the
 *   database rather than a file, preserving the original data while enabling proper persistence
 *   semantics and testability.
 * - PARSE-ACCOUNT-LINE (UNSTRING ... DELIMITED BY ",") is reproduced here as a simple
 *   String.split(",", -1) with trimming, mapping tokens positionally to
 *   firstName / lastName / birthDate / balance – exactly the order defined in the COBOL FD.
 * - WS-Balance was PIC X(20) in COBOL (stored as a string). It is parsed to BigDecimal here so
 *   that the domain model can treat monetary values correctly.
 * - If 'accounts.txt' is absent the application logs a clear warning instead of crashing, which
 *   fulfils the Operational Requirement that the modernised system must surface a user-intelligible
 *   error rather than abnormally terminating as the original COBOL program would have done.
 */
@Component
public class AccountFileLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountFileLoader.class);

    // Number of comma-delimited fields expected per record, matching the COBOL UNSTRING target list.
    private static final int EXPECTED_FIELD_COUNT = 4;
    private static final int IDX_FIRST_NAME = 0;
    private static final int IDX_LAST_NAME  = 1;
    private static final int IDX_BIRTH_DATE = 2;
    private static final int IDX_BALANCE    = 3;

    private final AccountRepository accountRepository;

    // Configurable file path; defaults to 'accounts.txt' in the working directory,
    // matching the COBOL ASSIGN TO "accounts.txt" clause.
    @Value("${banking.accounts-file:accounts.txt}")
    private String accountsFilePath;

    public AccountFileLoader(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path filePath = Paths.get(accountsFilePath);
        log.info("Loading accounts from file: {}", filePath.toAbsolutePath());

        if (!Files.exists(filePath)) {
            // Surface a clear warning rather than crashing (Operational Requirement).
            log.warn(
                "Accounts file '{}' not found. No accounts will be available for lookup. "
                    + "Please ensure the file exists at the configured path (banking.accounts-file).",
                filePath.toAbsolutePath()
            );
            return;
        }

        List<Account> accounts = new ArrayList<>();
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Skip blank lines silently.
                if (line.isBlank()) {
                    continue;
                }
                parseLine(line, lineNumber).ifPresent(accounts::add);
            }
        } catch (FileNotFoundException e) {
            log.warn(
                "Accounts file '{}' disappeared before it could be read: {}",
                filePath.toAbsolutePath(), e.getMessage()
            );
            return;
        } catch (IOException e) {
            log.error(
                "Unexpected I/O error while reading accounts file '{}': {}",
                filePath.toAbsolutePath(), e.getMessage(), e
            );
            return;
        }

        if (accounts.isEmpty()) {
            log.warn("Accounts file was read but contained no valid records.");
        } else {
            accountRepository.saveAll(accounts);
            log.info("Successfully loaded {} account(s) into the repository.", Optional.of(accounts.size()));
        }
    }

    /**
     * Parses a single comma-delimited line into an Account entity.
     *
     * Mirrors PARSE-ACCOUNT-LINE:
     *   UNSTRING AccountLine DELIMITED BY ","
     *     INTO WS-FirstName WS-LastName WS-BirthDate WS-Balance
     *
     * Matching is case-sensitive and no normalisation is applied (Data Quality Requirement).
     *
     * @param line       the raw text line from accounts.txt
     * @param lineNumber 1-based line number used for diagnostic logging
     * @return an Optional containing the parsed Account, or empty if the line is malformed
     */
    private java.util.Optional<Account> parseLine(String line, int lineNumber) {
        // Use -1 limit so trailing empty tokens are preserved, matching COBOL UNSTRING behaviour
        // where missing trailing fields leave the target variable empty.
        String[] tokens = line.split(",", -1);

        if (tokens.length < EXPECTED_FIELD_COUNT) {
            log.warn(
                "Line {} skipped – expected {} comma-separated fields but found {}: [{}]"
            );
            return java.util.Optional.empty();
        }

        String firstName = tokens[IDX_FIRST_NAME].trim();
        String lastName  = tokens[IDX_LAST_NAME].trim();
        String birthDate = tokens[IDX_BIRTH_DATE].trim();
        String rawBalance = tokens[IDX_BALANCE].trim();

        BigDecimal balance;
        try {
            // WS-Balance was PIC X(20) in COBOL; we parse to BigDecimal for proper monetary handling.
            balance = new BigDecimal(rawBalance);
        } catch (NumberFormatException e) {
            log.warn(
                "Line {} skipped – balance field '{}' cannot be parsed as a decimal number.",
                    Optional.of(lineNumber), rawBalance
            );
            return java.util.Optional.empty();
        }

        Account account = Account.builder()
            .firstName(firstName)
            .lastName(lastName)
            .birthDate(LocalDate.parse(birthDate))  // ISO-8601 format assumed (YYYY-MM-DD)
            .balance(balance)
            .build();

        return java.util.Optional.of(account);
    }
}
