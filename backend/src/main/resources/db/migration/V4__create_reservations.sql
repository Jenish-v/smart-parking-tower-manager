CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facilities (id),
    vehicle_identifier VARCHAR(32) NOT NULL,
    required_size VARCHAR(8) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT reservations_vehicle_identifier_format
        CHECK (vehicle_identifier ~ '^[A-Z0-9][A-Z0-9 -]*$'),
    CONSTRAINT reservations_required_size
        CHECK (required_size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT reservations_window_order CHECK (ends_at > starts_at),
    CONSTRAINT reservations_creation_order CHECK (created_at <= starts_at),
    CONSTRAINT reservations_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'FULFILLED', 'EXPIRED')),
    CONSTRAINT reservations_state_consistent CHECK (
        (status = 'CONFIRMED' AND resolved_at IS NULL)
        OR (status = 'CANCELLED' AND resolved_at >= created_at AND resolved_at < ends_at)
        OR (status = 'FULFILLED' AND resolved_at >= starts_at AND resolved_at < ends_at)
        OR (status = 'EXPIRED' AND resolved_at >= ends_at)
    )
);

CREATE INDEX reservations_facility_window_idx
    ON reservations (facility_id, starts_at, ends_at)
    WHERE status = 'CONFIRMED';

CREATE INDEX reservations_vehicle_history_idx
    ON reservations (facility_id, vehicle_identifier, created_at DESC);

