# Banking Account Lookup — Modernised Spring Boot Backend

## Overview

This project is a modernised Java/Spring Boot backend that replaces a legacy IBM mainframe COBOL program (`BankingApp`). The original system ran as a console-interactive COBOL program that sequentially scanned a flat file (`accounts.txt`) to look up a customer account balance by first and last name. The modernised system exposes the same business capability as a REST API backed by a relational database, while preserving all original business rules — including exact, case-sensitive name matching.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                        │
│         AccountController  /api/v1/accounts/lookup      │
└───────────────────────┬─────────────────────────────────┘
                        │ delegates to
┌───────────────────────▼─────────────────────────────────┐
│                   Service Layer                          │
│   AccountService — name parsing, matching, validation   │
└───────────────────────┬─────────────────────────────────┘
                        │ queries
┌───────────────────────▼─────────────────────────────────┐
│                 Repository Layer                         │
│        AccountRepository (Spring Data JPA)              │
└───────────────────────┬─────────────────────────────────┘
                        │ persists/reads
┌───────────────────────▼─────────────────────────────────┐
│                  Domain / Persistence                    │
│        Account entity  ←  AccountFileLoader             │
│        (H2 in-memory DB)    (seeds from accounts.txt)   │
└─────────────────────────────────────────────────────────┘
```

### Layers

| Layer | Class(es) | Responsibility |
|---|---|---|
| **Controller** | `AccountController` | Accepts HTTP requests, validates input DTOs, returns `ResponseEntity` |
| **Service** | `AccountService` | Parses full-name string, performs exact case-sensitive lookup, raises exceptions |
| **Repository** | `AccountRepository` | Spring Data JPA interface; queries `Account` entity |
| **Domain** | `Account` | JPA entity with `firstName`, `lastName`, `birthDate`, `balance` (BigDecimal) |
| **Loader** | `AccountFileLoader` | `ApplicationRunner` that reads `accounts.txt` at startup and seeds the database |
| **Error handling** | `GlobalExceptionHandler`, `AccountNotFoundException` | Uniform JSON error responses for all failure modes |

---

## How Legacy COBOL Logic Was Decoupled

### 1. Sequential File Scan → Database + Repository

The original COBOL program opened `accounts.txt` as a `LINE SEQUENTIAL` file, read every record in a `PERFORM UNTIL` loop, and compared fields in memory. In the modernised system:

- **`AccountFileLoader`** (an `ApplicationRunner`) reads `accounts.txt` once at application startup, parses each comma-delimited line, and persists every record into an H2 in-memory database via the JPA repository.
- **`AccountRepository`** then provides a single typed query `findByFirstNameAndLastName(...)` replacing the entire manual scan loop. This eliminates the stateful loop-control variable (`Line-End` / `HIGH-VALUES`) entirely.

### 2. WORKING-STORAGE State → Method Parameters and DTOs

The COBOL program relied heavily on WORKING-STORAGE fields (`WS-UserInput`, `WS-Input-FirstName`, `WS-Input-LastName`, `Name-Match`, `Line-End`) as shared mutable state across paragraphs. In the modernised system:

- All transient state is expressed as method parameters and local variables inside `AccountService`.
- The `Name-Match` flag is replaced by an `Optional<Account>` return value from the repository — present means matched, empty means not found.
- The `Line-End` loop-termination sentinel is replaced by the repository query itself (no explicit iteration).

### 3. PERFORM Paragraphs → Service Methods

| COBOL Paragraph | Java Equivalent |
|---|---|
| `MAIN-PROCEDURE` | `AccountService.lookupAccount(AccountLookupRequest)` |
| `PARSE-USER-INPUT` | `AccountService.parseFullName(String)` (private method) |
| `PARSE-ACCOUNT-LINE` | `AccountFileLoader.parseLine(String)` (at load time) |

### 4. UNSTRING (Space Delimiter) → String Split with Validation

The COBOL `UNSTRING WS-UserInput DELIMITED BY SPACE` had undefined behaviour when no space was present (last name would be empty). The modernised system:

- Validates that the input contains at least one space via Bean Validation on `AccountLookupRequest`.
- Uses `String.split(" ", 2)` in `AccountService.parseFullName(...)` to mirror the two-token extraction.
- Returns a clear validation error to the caller rather than silently failing to find a match.

### 5. Console I/O → REST API

The original `DISPLAY` / `ACCEPT` interaction with the console is replaced by a single REST endpoint:

```
POST /api/v1/accounts/lookup
Content-Type: application/json

{ "fullName": "John Smith" }
```

The response returns either the matched account balance or a structured not-found message, rather than writing strings to stdout.

### 6. Case-Sensitive Matching Preserved

Per the original COBOL `IF WS-Input-FirstName = WS-FirstName AND WS-Input-LastName = WS-LastName` comparison (no `INSPECT`/`UPPER` transformation applied), the modernised system performs **exact, case-sensitive** string matching. `john smith` will **not** match `John Smith`. This is enforced at the repository query level using standard JPA equality, and documented as an explicit business rule.

### 7. Missing File → User-Intelligible Error

The original COBOL program had no error handling for a missing or inaccessible `accounts.txt` — it would abnormally terminate. The modernised `AccountFileLoader` catches `IOException` and throws a descriptive `IllegalStateException` that prevents application startup, surfacing a clear message in the logs.

---

## Prerequisites

- **Java 21**
- **Maven 3.9+**
- An `accounts.txt` file in the location configured by `app.accounts-file-path` (default: `./accounts.txt`)

### accounts.txt Format

Each line must be a comma-delimited record with exactly four fields:

```
John,Smith,1980-04-15,12345.67
Jane,Doe,1992-08-30,9876.00
```

Fields: `FirstName,LastName,BirthDate(YYYY-MM-DD),Balance`

---

## Building and Running

```bash
# Clone the repository
git clone <repository-url>
cd banking-lookup

# Place your accounts.txt in the project root (or configure the path below)
cp /path/to/accounts.txt ./accounts.txt

# Build
mvn clean package

# Run
java -jar target/banking-lookup-1.0.0.jar

# Or run directly with Maven
mvn spring-boot:run
```

### Configuring the accounts.txt path

Edit `src/main/resources/application.properties`:

```properties
app.accounts-file-path=/absolute/or/relative/path/to/accounts.txt
```

---

## API Usage

### Look up an account balance

**Request**
```http
POST /api/v1/accounts/lookup
Content-Type: application/json

{
  "fullName": "John Smith"
}
```

**Response — match found (200 OK)**
```json
{
  "found": true,
  "firstName": "John",
  "lastName": "Smith",
  "balance": "12345.67",
  "message": null
}
```

**Response — no match (404 Not Found)**
```json
{
  "found": false,
  "firstName": null,
  "lastName": null,
  "balance": null,
  "message": "Kein Konto für John Smith gefunden."
}
```

**Response — validation error (400 Bad Request)**
```json
{
  "status": 400,
  "error": "Validation Failed",
  "details": ["fullName must contain a space between first and last name"]
}
```

---

## Running Tests

```bash
mvn test
```

Tests cover:
- **`AccountServiceTest`** — match found, not found, missing space in input, empty input
- **`AccountControllerTest`** — happy path (MockMvc), validation failure, 404 response

---

## Technology Stack

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Persistence | Spring Data JPA + H2 (in-memory) |
| Validation | Jakarta Bean Validation (`@Valid`) |
| Build | Maven |
| Boilerplate reduction | Lombok |
| Testing | JUnit 5, Spring Boot Test, MockMvc |

---

## Key Design Decisions

1. **H2 in-memory database** — chosen because the original system treated `accounts.txt` as a read-only flat file. The loader seeds the database at startup. For production use, replace with a persistent datasource (PostgreSQL, etc.) and a proper ETL or migration process.
2. **`BigDecimal` for balance** — the COBOL `WS-Balance PIC X(20)` stored balance as a string; the modernised domain model converts it to `BigDecimal` on load for type safety and arithmetic correctness.
3. **No verb in URL** — the endpoint is `POST /api/v1/accounts/lookup` where `lookup` identifies the sub-resource action; a pure resource design could use `GET /api/v1/accounts?firstName=John&lastName=Smith`, which is equally valid and may be preferred.
4. **Case-sensitive matching** — explicitly preserved from the COBOL source as a documented business rule; normalisation would require a product decision, not a developer assumption.
