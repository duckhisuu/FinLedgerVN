-- ==============================================================================
-- FinLedger VN - Flyway Migration V4
-- Personal Finance Management: Users, Authentication, Wallets, Categories & Budgets
-- ==============================================================================

-- 1. Table: users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    default_currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    avatar_color VARCHAR(32) NOT NULL DEFAULT '#22c55e',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email_lower ON users (LOWER(email));

-- 2. Enrich accounts table with multi-tenant user ownership & wallet/category metadata
ALTER TABLE accounts
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN account_subtype VARCHAR(32),
    ADD COLUMN icon VARCHAR(64),
    ADD COLUMN color VARCHAR(32),
    ADD COLUMN bank_code VARCHAR(32),
    ADD COLUMN account_number VARCHAR(64),
    ADD COLUMN include_in_total BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_accounts_user_type ON accounts (user_id, account_type);
CREATE INDEX idx_accounts_user_subtype ON accounts (user_id, account_subtype);

-- 3. Enrich journal_entries table with user context & transaction classification
-- (DDL column addition does not trigger row-level BEFORE UPDATE/DELETE immutability triggers)
ALTER TABLE journal_entries
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN entry_type VARCHAR(32),
    ADD COLUMN wallet_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN category_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT;

CREATE INDEX idx_journal_entries_user_date ON journal_entries (user_id, transaction_date DESC);

-- 4. Enrich expense_groups, bank_account_mappings & categorization_rules with user ownership
ALTER TABLE expense_groups
    ADD COLUMN owner_user_id UUID REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_expense_groups_owner ON expense_groups (owner_user_id);

ALTER TABLE bank_account_mappings
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE categorization_rules
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- 5. Table: budgets (Monthly Personal & Category Spending Limits)
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_account_id UUID REFERENCES accounts(id) ON DELETE CASCADE,
    month_year VARCHAR(7) NOT NULL,
    amount_limit NUMERIC(19, 4) NOT NULL CHECK (amount_limit > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_budgets_user_cat_month
    ON budgets (user_id, category_account_id, month_year)
    WHERE category_account_id IS NOT NULL;

CREATE UNIQUE INDEX uq_budgets_user_total_month
    ON budgets (user_id, month_year)
    WHERE category_account_id IS NULL;

CREATE INDEX idx_budgets_user_month ON budgets (user_id, month_year);

