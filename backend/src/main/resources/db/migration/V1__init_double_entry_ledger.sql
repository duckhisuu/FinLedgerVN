-- ==============================================================================
-- FinLedger VN - Flyway Migration V1
-- Double-Entry Ledger Engine & Immutability Trigger
-- ==============================================================================

-- 1. Table: accounts
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    account_type VARCHAR(32) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    cached_balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    allow_negative BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table: journal_entries
CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_date TIMESTAMPTZ NOT NULL,
    description TEXT NOT NULL,
    reference_id VARCHAR(128) UNIQUE,
    reversal_of_id UUID REFERENCES journal_entries(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Table: posting_legs
CREATE TABLE posting_legs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount <> 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for high performance ledger queries
CREATE INDEX idx_posting_legs_account_created ON posting_legs (account_id, created_at);
CREATE INDEX idx_posting_legs_journal_entry ON posting_legs (journal_entry_id);

-- 4. Database Trigger protecting Immutability (Append-Only)
CREATE OR REPLACE FUNCTION trg_prevent_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable. Use a reversing journal entry instead.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_journal_entries_mutation
BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW
EXECUTE FUNCTION trg_prevent_ledger_mutation();

CREATE TRIGGER trg_prevent_posting_legs_mutation
BEFORE UPDATE OR DELETE ON posting_legs
FOR EACH ROW
EXECUTE FUNCTION trg_prevent_ledger_mutation();
