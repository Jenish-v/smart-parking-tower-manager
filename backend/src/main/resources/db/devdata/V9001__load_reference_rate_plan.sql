INSERT INTO pricing_rate_plans (
    id,
    version,
    name,
    effective_from,
    effective_until,
    grace_seconds,
    billing_increment_seconds,
    currency
) VALUES (
    'acd13eb1-c151-4c4c-a83b-dd16c11bd0ef',
    1,
    'Reference CAD rate',
    '2000-01-01T00:00:00Z',
    NULL,
    600,
    900,
    'CAD'
);

INSERT INTO pricing_rate_bands (
    rate_plan_id,
    rate_plan_version,
    size_class,
    increment_charge_minor,
    rolling_day_cap_minor
) VALUES
    ('acd13eb1-c151-4c4c-a83b-dd16c11bd0ef', 1, 'SMALL', 125, 2000),
    ('acd13eb1-c151-4c4c-a83b-dd16c11bd0ef', 1, 'MEDIUM', 150, 2500),
    ('acd13eb1-c151-4c4c-a83b-dd16c11bd0ef', 1, 'LARGE', 200, 3000);
