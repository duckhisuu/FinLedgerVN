-- ==============================================================================
-- FinLedger VN - Flyway Migration V3
-- Group Expense Splitting, Penny-Exact Shares & VietQR Settlement Engine
-- ==============================================================================

-- 1. Table: expense_groups
CREATE TABLE expense_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    share_token VARCHAR(64) UNIQUE NOT NULL,
    simplify_debts BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_expense_groups_token ON expense_groups(share_token);

-- 2. Table: group_members
CREATE TABLE group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    is_guest BOOLEAN NOT NULL DEFAULT true,
    bank_code VARCHAR(32),
    bank_account_number VARCHAR(64),
    member_ledger_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_group_members_group_id ON group_members(group_id);
CREATE INDEX idx_group_members_ledger_acc ON group_members(member_ledger_account_id);

-- 3. Table: group_expenses
CREATE TABLE group_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
    paid_by_member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL CHECK (total_amount > 0),
    split_strategy VARCHAR(32) NOT NULL CHECK (split_strategy IN ('EQUAL', 'EXACT', 'PERCENTAGE', 'SHARES')),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_group_expenses_group_id ON group_expenses(group_id);
CREATE INDEX idx_group_expenses_paid_by ON group_expenses(paid_by_member_id);

-- 4. Table: group_expense_shares
CREATE TABLE group_expense_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_expense_id UUID NOT NULL REFERENCES group_expenses(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE RESTRICT,
    share_amount NUMERIC(19, 4) NOT NULL CHECK (share_amount >= 0),
    raw_weight NUMERIC(19, 4),
    CONSTRAINT uq_expense_member UNIQUE (group_expense_id, member_id)
);

CREATE INDEX idx_group_expense_shares_exp ON group_expense_shares(group_expense_id);
CREATE INDEX idx_group_expense_shares_mem ON group_expense_shares(member_id);

-- 5. Table: debt_settlements
CREATE TABLE debt_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
    from_member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE RESTRICT,
    to_member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    settlement_code VARCHAR(64) UNIQUE NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
    journal_entry_id UUID REFERENCES journal_entries(id) ON DELETE SET NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_debt_settlements_group_status ON debt_settlements(group_id, status);
CREATE INDEX idx_debt_settlements_code ON debt_settlements(settlement_code);
