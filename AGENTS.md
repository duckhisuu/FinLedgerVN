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

## 3. High-Craft Design System (VitalCare Aesthetic & Strict Anti-AI-Slop Rules)
Reference Benchmark: `https://vital-care-fe.vercel.app/`

### 3.1. STRICT ANTI-AI-SLOP BAN LIST (NEVER DO THESE)
1. **NO Centered Text-Only Heroes:** NEVER build a landing page hero that is just a centered pill badge, a giant centered H1 with gradient text, two centered buttons, and empty black space around it.
2. **NO Rainbow Stat Rows:** NEVER render a row of 4 bare numbers in 4 different random colors (green, blue, purple, pink) separated only by a horizontal line. All metrics must live inside structured Glass Bento containers with unified monochrome numerals and tiny telemetry status dots.
3. **NO Cheesy Multi-Word Text Gradients:** Do NOT slap bright cyan-to-green `bg-clip-text` gradients onto half of a sentence. Use crisp high-contrast pure white (`#F8FAFC`) shifting subtly into metallic zinc (`#94A3B8`), reserving neon accents strictly for live telemetry indicators, badges, and interactive borders.
4. **NO Bare Empty Black Backgrounds:** Every major section must sit on top of an architectural background layer: a subtle SVG coordinate grid (`bg-[linear-gradient(to_right,#ffffff06_1px,transparent_1px),linear-gradient(to_bottom,#ffffff06_1px,transparent_1px)] bg-[size:36px_36px]`) with a radial fade mask and subtle ambient lighting.
5. **NO Underlined `đ` Symbol:** NEVER use the raw lowercase `đ` with an underline for currency. Always format currency as `12.500.000` in `JetBrains Mono` (`tabular-nums`) followed by a clean `VND` badge or a non-underlined `₫` in smaller muted text.

### 3.2. VitalCare Visual Architecture
- **Floating Glass Navbar:** Top navigation must be a floating glass dock (`fixed top-4 inset-x-0 max-w-6xl mx-auto rounded-full bg-[#0B0F17]/75 backdrop-blur-2xl border border-white/[0.08] px-6 py-3 shadow-[0_20px_50px_rgba(0,0,0,0.6)]`).
- **Asymmetric Interactive Hero:**
  - Left 50%: Technical telemetry header (`SYS.ENGINE // DOUBLE-ENTRY v2.4`), crisp Vietnamese editorial typography (`Be Vietnam Pro` or `Plus Jakarta Sans`), and precision CTA buttons.
  - Right 50%: A multi-layered, interactive **Live Financial Telemetry Console** (showing real-time Webhook -> Redis Lock -> Double-Entry Split -> VietQR SSE settlement in action).
- **Interactive Bento Feature Grid:** Every Bento card MUST include a functional mini-UI visualization (live animated ledger rows, laser scanning receipt OCR preview, interactive SVG debt graph nodes), NOT just static icons and paragraphs.