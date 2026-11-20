-- V5: Vietcombank (VCB) & MoMo Multi-Source Balance Ingestion, Fuzzy Deduplication & Auto Internal Transfer

ALTER TABLE bank_account_mappings
    ADD COLUMN provider_code VARCHAR(32) NOT NULL DEFAULT 'SEPAY',
    ADD COLUMN account_identifier VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN webhook_secret_token VARCHAR(128) NOT NULL DEFAULT '',
    ADD COLUMN last_reported_balance NUMERIC(19, 4),
    ADD COLUMN last_synced_at TIMESTAMPTZ;

UPDATE bank_account_mappings
SET provider_code = CASE
        WHEN UPPER(COALESCE(bank_name, '')) LIKE '%VCB%'
          OR UPPER(COALESCE(bank_name, '')) LIKE '%VIETCOMBANK%' THEN 'VCB'
        WHEN UPPER(COALESCE(bank_name, '')) LIKE '%MOMO%' THEN 'MOMO'
        ELSE 'SEPAY'
    END,
    account_identifier = bank_account_number,
    webhook_secret_token = CASE
        WHEN webhook_secret_token IS NULL OR webhook_secret_token = ''
            THEN 'flb_' || REPLACE(id::text, '-', '')
        ELSE webhook_secret_token
    END;

CREATE INDEX idx_bam_provider_identifier
    ON bank_account_mappings (provider_code, account_identifier);

CREATE INDEX idx_bam_webhook_secret_token
    ON bank_account_mappings (webhook_secret_token);

CREATE TABLE ingestion_fingerprints (
    id UUID PRIMARY KEY,
    source_account_id UUID NOT NULL REFERENCES accounts (id),
    transfer_direction VARCHAR(8) NOT NULL CHECK (transfer_direction IN ('IN', 'OUT')),
    exact_amount NUMERIC(19, 4) NOT NULL,
    time_bucket_5m BIGINT NOT NULL,
    normalized_ref_code VARCHAR(64),
    fuzzy_signature VARCHAR(128) NOT NULL UNIQUE,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries (id),
    sources_merged VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ifp_lookup
    ON ingestion_fingerprints (source_account_id, transfer_direction, exact_amount, time_bucket_5m);

CREATE INDEX idx_ifp_ref_code
    ON ingestion_fingerprints (normalized_ref_code)
    WHERE normalized_ref_code IS NOT NULL;

CREATE INDEX idx_ifp_journal_entry_id
    ON ingestion_fingerprints (journal_entry_id);

