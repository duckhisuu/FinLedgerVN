-- ==============================================================================
-- FinLedger VN - Flyway Migration V2
-- Bank Account Mapping, Categorization Rules & Webhook Inbox Logs
-- ==============================================================================

-- 1. Table: bank_account_mappings
CREATE TABLE bank_account_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_number VARCHAR(64) NOT NULL UNIQUE,
    bank_name VARCHAR(64) NOT NULL,
    ledger_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    default_suspense_expense_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    default_suspense_revenue_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bank_mappings_account_num ON bank_account_mappings(bank_account_number);

-- 2. Table: categorization_rules
CREATE TABLE categorization_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keyword_pattern VARCHAR(255) NOT NULL,
    transfer_type VARCHAR(10) NOT NULL CHECK (transfer_type IN ('IN', 'OUT')),
    target_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rules_priority_active ON categorization_rules(priority DESC, active);

-- 3. Table: webhook_inbox_logs (Audit log for all incoming webhooks)
CREATE TABLE webhook_inbox_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(64),
    external_transaction_id VARCHAR(128) NOT NULL,
    sanitized_payload TEXT,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PROCESSED', 'DUPLICATE', 'FAILED')),
    journal_entry_id UUID REFERENCES journal_entries(id) ON DELETE SET NULL,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_inbox_provider_tx ON webhook_inbox_logs(provider, external_transaction_id);
