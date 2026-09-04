CREATE TABLE pricing_rate_plans (
    id UUID NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_until TIMESTAMP WITH TIME ZONE,
    grace_seconds BIGINT NOT NULL,
    billing_increment_seconds BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    PRIMARY KEY (id, version),
    CONSTRAINT pricing_rate_plans_version_positive CHECK (version > 0),
    CONSTRAINT pricing_rate_plans_name_present CHECK (length(trim(name)) > 0),
    CONSTRAINT pricing_rate_plans_effective_window CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT pricing_rate_plans_grace_range CHECK (grace_seconds >= 0 AND grace_seconds < 86400),
    CONSTRAINT pricing_rate_plans_increment_range CHECK (
        billing_increment_seconds > 0 AND billing_increment_seconds <= 86400
    ),
    CONSTRAINT pricing_rate_plans_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    EXCLUDE USING gist (
        tstzrange(effective_from, effective_until, '[)') WITH &&
    )
);

CREATE TABLE pricing_rate_bands (
    rate_plan_id UUID NOT NULL,
    rate_plan_version BIGINT NOT NULL,
    size_class VARCHAR(8) NOT NULL,
    increment_charge_minor BIGINT NOT NULL,
    rolling_day_cap_minor BIGINT NOT NULL,
    PRIMARY KEY (rate_plan_id, rate_plan_version, size_class),
    FOREIGN KEY (rate_plan_id, rate_plan_version)
        REFERENCES pricing_rate_plans (id, version),
    CONSTRAINT pricing_rate_bands_size CHECK (size_class IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT pricing_rate_bands_charge_positive CHECK (increment_charge_minor > 0),
    CONSTRAINT pricing_rate_bands_cap_valid CHECK (rolling_day_cap_minor >= increment_charge_minor)
);

CREATE TABLE parking_receipts (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES parking_sessions (id),
    rate_plan_id UUID NOT NULL,
    rate_plan_version BIGINT NOT NULL,
    size_class VARCHAR(8) NOT NULL,
    entered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    exited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    billable_seconds BIGINT NOT NULL,
    billable_nanos INTEGER NOT NULL,
    billing_increments BIGINT NOT NULL,
    gross_charge_minor BIGINT NOT NULL,
    cap_discount_minor BIGINT NOT NULL,
    total_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (rate_plan_id, rate_plan_version)
        REFERENCES pricing_rate_plans (id, version),
    CONSTRAINT parking_receipts_size CHECK (size_class IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT parking_receipts_time_order CHECK (exited_at >= entered_at),
    CONSTRAINT parking_receipts_billable_non_negative CHECK (billable_seconds >= 0),
    CONSTRAINT parking_receipts_billable_nanos_range CHECK (billable_nanos >= 0 AND billable_nanos < 1000000000),
    CONSTRAINT parking_receipts_increments_non_negative CHECK (billing_increments >= 0),
    CONSTRAINT parking_receipts_amounts_non_negative CHECK (
        gross_charge_minor >= 0
        AND cap_discount_minor >= 0
        AND total_minor >= 0
    ),
    CONSTRAINT parking_receipts_total_consistent CHECK (
        gross_charge_minor - cap_discount_minor = total_minor
    ),
    CONSTRAINT parking_receipts_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX parking_receipts_rate_plan_idx
    ON parking_receipts (rate_plan_id, rate_plan_version);
