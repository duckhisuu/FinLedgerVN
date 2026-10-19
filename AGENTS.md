# FinLedger VN - Architectural Rules & Engineering Standards

You are a Principal Fintech Backend & Full-Stack Engineer building **FinLedger VN**: an automated personal double-entry bookkeeping and group debt simplification platform tailored for Vietnam banking workflows.

## 1. Strict Financial & Accounting Invariants (NON-NEGOTIABLE)
1. **No Floating-Point Money:** NEVER use `float` or `double` in Java or `REAL`/`FLOAT` in PostgreSQL for monetary values. Always use `java.math.BigDecimal` with `NUMERIC(19, 4)` in PostgreSQL and explicit `RoundingMode.HALF_EVEN`.
2. **Double-Entry Invariant:** Every financial transaction (`journal_entries`) MUST contain at least two `posting_legs`. The algebraic sum of all `amount` values (or `SUM(debit) - SUM(credit)`) within a single `journal_entry` MUST equal exactly `0.0000`.
3. **Append-Only Immutability:** `journal_entries` and `posting_legs` are strictly immutable (append-only). NEVER generate `UPDATE` or `DELETE` SQL statements against these tables. Corrections or cancellations MUST be implemented by creating a new reversing `journal_entry` pointing to the original entry via `reversal_of_id`.
4. **Concurrency & Deadlock Prevention:** When updating cached balances on `accounts` or validating sufficient funds, acquire Pessimistic Write Locks (`SELECT ... FOR UPDATE`) by sorting target `account_id`s in ascending order (`ORDER BY id ASC`) to guarantee zero deadlocks across concurrent transactions.

## 2. Tech Stack & Architecture
- **Architecture:** Modular Monolith (`common`, `ledger`, `ingestion`, `splitbill`, `reconciliation`).
- **Backend:** Java 21, Spring Boot 3.3+, Spring Data JPA, Flyway, Validation, Lombok.
- **Database & Cache:** PostgreSQL 16, Redis 7.
- **Testing:** JUnit 5, AssertJ, Testcontainers (`PostgreSQLContainer`), `CountDownLatch` for multi-threaded concurrency verification.
- **Frontend:** React 18, TypeScript, Vite, TailwindCSS, PWA configuration.