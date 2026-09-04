CREATE TABLE fee_adjustments (
    id UUID PRIMARY KEY,
    receipt_id UUID NOT NULL REFERENCES parking_receipts (id),
    amount_minor BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    reason_detail VARCHAR(240) NOT NULL,
    operator_reference VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fee_adjustments_amount_non_zero CHECK (amount_minor <> 0),
    CONSTRAINT fee_adjustments_reason CHECK (
        reason IN ('CUSTOMER_SERVICE', 'RATE_CORRECTION', 'OPERATIONAL_EXCEPTION', 'OTHER')
    ),
    CONSTRAINT fee_adjustments_reason_detail_present CHECK (length(trim(reason_detail)) > 0),
    CONSTRAINT fee_adjustments_operator_present CHECK (length(trim(operator_reference)) > 0)
);

CREATE INDEX fee_adjustments_receipt_history_idx
    ON fee_adjustments (receipt_id, created_at, id);
