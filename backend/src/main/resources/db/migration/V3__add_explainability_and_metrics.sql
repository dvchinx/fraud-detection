ALTER TABLE transactions
    ADD COLUMN decided_at TIMESTAMP,
    ADD COLUMN rule_outcomes JSONB,
    ADD COLUMN ml_risk_score DOUBLE PRECISION,
    ADD COLUMN ml_model_version VARCHAR(50),
    ADD COLUMN ml_base_value DOUBLE PRECISION,
    ADD COLUMN ml_top_factors JSONB,
    ADD COLUMN confirmed_fraud BOOLEAN;

CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_confirmed_fraud ON transactions (confirmed_fraud) WHERE confirmed_fraud IS NOT NULL;
