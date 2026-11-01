# Banking Account Lookup — Legacy Modernisation

This project is a modern Java/Spring Boot backend that replaces a legacy COBOL program (`BankingApp`) originally running on an IBM mainframe. The original program read customer names from an operator console, searched a sequential flat file (`accounts.txt`) for a matching record, and displayed the account balance or a not-found message.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Legacy-to-Modern Mapping Decisions](#legacy-to-modern-mapping-decisions)
3. [Project Structure](#project-structure)
4. [Prerequisites](#prerequisites)
5. [Setup & Running](#setup--running)
6. [REST API Reference](#rest-api-reference)
7. [Running Tests](#running-tests)
8. [Design Decisions & Notes](#design-decisions--notes)

---

## Architecture Overview

The application follows a classic layered Spring Boot architecture:

```
HTTP Client
    │
    ▼
┌─────────────────────────┐
│   AccountController     │  REST layer  — validates input, delegates to service
│   /api/v1/accounts      │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│   AccountService        │  Business logic — encapsulates search & match rules
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│   AccountRepository     │  Data access — Spring Data JPA, H2 (dev) / any RDBMS
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│   Account (JPA Entity)  │  Domain model — firstName, lastName, birthDate, balance
└─────────────────────────┘
```

Cross-cutting concerns:
- **GlobalExceptionHandler** (`@ControllerAdvice`) — centralised HTTP error responses
- **Bean Validation** (`@Valid`) — input validation at the API boundary
- **AccountNotFoundException** — typed exception for the no-match business rule

---

## Legacy-to-Modern Mapping Decisions

| COBOL Construct | Modern Equivalent | Rationale |
|---|---|---|
| `WORKING-STORAGE` variables (`WS-FirstName`, `WS-LastName`, etc.) | Fields in `AccountSearchRequest` / `AccountSearchResponse` DTOs and `Account` entity | Global mutable state replaced with scoped, immutable-friendly data transfer objects |
| `ACCEPT WS-UserInput` (operator console) | `POST /api/v1/accounts/search` request body (`AccountSearchRequest`) | Console I/O translated to a REST endpoint; operator input becomes a validated JSON payload |
| `UNSTRING WS-UserInput DELIMITED BY SPACE` (`PARSE-USER-INPUT`) | `@NotBlank` + split logic in `AccountService.parseFullName()` | The UNSTRING paragraph is a pure parsing concern; encapsulated as a private service method |
| Sequential flat file `AccountFile` (comma-delimited records) | `Account` JPA entity + `AccountRepository` (Spring Data JPA) | Flat-file storage replaced with a relational database; the comma-delimited record structure maps directly to entity fields |
| `UNSTRING AccountLine DELIMITED BY ","` (`PARSE-ACCOUNT-LINE`) | JPA column mapping on `Account` entity fields | The parsing paragraph is no longer needed; the ORM handles field mapping |
| `PERFORM UNTIL Line-End = HIGH-VALUES` loop with `AT END` sentinel | `AccountRepository.findByFirstNameAndLastName()` query method | The sequential search loop is replaced by an indexed database query — O(1) vs O(n) |
| `IF Name-Match = "N"` → display not-found message | `AccountNotFoundException` thrown by `AccountService`, handled by `GlobalExceptionHandler` | Binary flag replaced with a typed exception; HTTP 404 communicates the not-found outcome semantically |
| `Name-Match` flag (`'Y'`/`'N'`) | Return value presence / exception absence | Boolean character flags replaced with idiomatic Java Optional/exception flow |
| `WS-Balance` (`PIC X(20)`) | `BigDecimal balance` on `Account` entity | Alphanumeric balance field promoted to `BigDecimal` to enable safe monetary arithmetic |
| `STOP RUN` | Normal HTTP response cycle / Spring Boot lifecycle | Program termination replaced by stateless request/response handling |
| German-language console messages (`'Bitte geben Sie...'`, `'Kein Konto für...'`) | Structured JSON error/success responses; messages externalised to constants | Human-readable console strings replaced with machine-readable API responses |

---

## Project Structure

```
bankingapp/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/banking/
    │   │   ├── BankingApplication.java
    │   │   ├── controller/
    │   │   │   └── AccountController.java
    │   │   ├── domain/
    │   │   │   └── Account.java
    │   │   ├── dto/
    │   │   │   ├── AccountSearchRequest.java
    │   │   │   └── AccountSearchResponse.java
    │   │   ├── exception/
    │   │   │   ├── AccountNotFoundException.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── repository/
    │   │   │   └── AccountRepository.java
    │   │   └── service/
    │   │       └── AccountService.java
    │   └── resources/
    │       ├── application.properties
    │       └── data.sql
    └── test/
        └── java/com/banking/
            ├── controller/
            │   └── AccountControllerTest.java
            └── service/
                └── AccountServiceTest.java
```

---

## Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 21 or higher |
| Maven | 3.9 or higher |
| (Optional) Docker | For running with an external database |

No external database installation is required for development or testing — the application uses an **H2 in-memory database** by default, seeded automatically via `data.sql`.

---

## Setup & Running

### 1. Clone / obtain the project

```bash
git clone <repository-url>
cd bankingapp
```

### 2. Build the project

```bash
mvn clean package
```

### 3. Run the application

```bash
mvn spring-boot:run
```

Or run the packaged JAR directly:

```bash
java -jar target/bankingapp-0.0.1-SNAPSHOT.jar
```

The application starts on **http://localhost:8080** by default.

### 4. Verify startup

Open the H2 console (development only) at:

```
http://localhost:8080/h2-console
```

Use the JDBC URL `jdbc:h2:mem:bankingdb`, username `sa`, and an empty password.

---

## REST API Reference

### Search for an account by customer name

**Endpoint:** `POST /api/v1/accounts/search`

**Request Body:**

```json
{
  "firstName": "Hans",
  "lastName": "Müller"
}
```

| Field | Type | Constraints |
|---|---|---|
| `firstName` | `String` | Required, not blank, max 30 characters |
| `lastName` | `String` | Required, not blank, max 30 characters |

**Success Response — 200 OK:**

```json
{
  "firstName": "Hans",
  "lastName": "Müller",
  "balance": "1500.75",
  "found": true
}
```

**Not Found Response — 404 Not Found:**

```json
{
  "message": "Kein Konto für Hans Müller gefunden.",
  "found": false
}
```

**Validation Error Response — 400 Bad Request:**

```json
{
  "errors": {
    "firstName": "must not be blank"
  }
}
```

---

## Running Tests

```bash
mvn test
```

The test suite covers:

- **AccountServiceTest** — unit tests for the core business logic:
  - Match found by exact first name and last name
  - No match found throws `AccountNotFoundException`
  - Input with extra whitespace is handled correctly
  - Empty name segments are rejected

- **AccountControllerTest** — Spring MVC slice tests (`@WebMvcTest`):
  - HTTP 200 with correct response body on a successful lookup
  - HTTP 404 with a descriptive message when no account is found
  - HTTP 400 when request body fails Bean Validation

---

## Design Decisions & Notes

### Why POST for search instead of GET?

The original COBOL program accepted a full name string (first name + last name) as a single space-delimited input. Encoding a full name reliably as a `GET` query parameter introduces URL-encoding complexity and potential issues with special characters (e.g. German umlauts). Using `POST` with a structured JSON body is cleaner, safer, and aligns with the original intent of submitting structured input for processing.

### Why is the flat file replaced by a database?

The COBOL program performed a full sequential scan of a comma-delimited flat file on every search. This is an O(n) operation that does not scale. Replacing the file with a relational database and an indexed `findByFirstNameAndLastName` query reduces lookup time and enables concurrent access by multiple users — a fundamental requirement for an online backend service.

### Why BigDecimal for balance?

The original `WS-Balance` field was defined as `PIC X(20)` — a plain alphanumeric string. In financial systems, balances must support precise decimal arithmetic without floating-point rounding errors. `BigDecimal` is the Java standard for monetary values and satisfies this requirement.

### Legacy flag `Name-Match` removed

The `Name-Match = 'Y'/'N'` flag was a procedural workaround to track whether a sequential file scan found a result. In the modern design this is replaced by a typed `AccountNotFoundException`, which communicates the not-found outcome through the normal exception/HTTP mechanism. There is no need for a mutable flag.

### German-language messages preserved in responses

The original program displayed German messages to the operator console. The not-found message (`"Kein Konto für ... gefunden."`) is preserved in the API error response body to maintain continuity with the business domain language, while the API contract itself uses standard English field names.
