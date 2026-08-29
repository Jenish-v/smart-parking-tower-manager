CREATE TABLE active_allocations (
    id UUID PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facilities (id),
    space_id UUID NOT NULL REFERENCES parking_spaces (id),
    vehicle_identifier VARCHAR(32) NOT NULL,
    required_size VARCHAR(8) NOT NULL,
    allocated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT active_allocations_vehicle_identifier_format
        CHECK (vehicle_identifier ~ '^[A-Z0-9][A-Z0-9 -]*$'),
    CONSTRAINT active_allocations_required_size
        CHECK (required_size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT active_allocations_release_order
        CHECK (released_at IS NULL OR released_at >= allocated_at)
);

CREATE UNIQUE INDEX active_allocations_vehicle_active_unique
    ON active_allocations (facility_id, vehicle_identifier)
    WHERE released_at IS NULL;

CREATE UNIQUE INDEX active_allocations_space_active_unique
    ON active_allocations (space_id)
    WHERE released_at IS NULL;

CREATE INDEX active_allocations_facility_vehicle_history_idx
    ON active_allocations (facility_id, vehicle_identifier, allocated_at DESC);

CREATE INDEX active_allocations_space_history_idx
    ON active_allocations (space_id, allocated_at DESC);
