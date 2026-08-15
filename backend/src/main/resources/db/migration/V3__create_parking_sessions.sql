CREATE TABLE parking_sessions (
    id UUID PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facilities (id),
    vehicle_identifier VARCHAR(32) NOT NULL,
    required_size VARCHAR(8) NOT NULL,
    floor_number SMALLINT NOT NULL,
    zone_code VARCHAR(8) NOT NULL,
    space_number SMALLINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    entered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    exited_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT parking_sessions_vehicle_identifier_format
        CHECK (vehicle_identifier ~ '^[A-Z0-9][A-Z0-9 -]*$'),
    CONSTRAINT parking_sessions_required_size
        CHECK (required_size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT parking_sessions_floor_number_positive CHECK (floor_number > 0),
    CONSTRAINT parking_sessions_zone_code_format CHECK (zone_code ~ '^[A-Z][A-Z0-9]{0,7}$'),
    CONSTRAINT parking_sessions_space_number_positive CHECK (space_number > 0),
    CONSTRAINT parking_sessions_status CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT parking_sessions_state_consistent CHECK (
        (status = 'ACTIVE' AND exited_at IS NULL)
        OR (status = 'COMPLETED' AND exited_at IS NOT NULL AND exited_at >= entered_at)
    )
);

CREATE UNIQUE INDEX parking_sessions_vehicle_active_unique
    ON parking_sessions (facility_id, vehicle_identifier)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX parking_sessions_space_active_unique
    ON parking_sessions (facility_id, floor_number, zone_code, space_number)
    WHERE status = 'ACTIVE';

CREATE INDEX parking_sessions_vehicle_history_idx
    ON parking_sessions (facility_id, vehicle_identifier, entered_at DESC);

CREATE TABLE parking_session_requests (
    request_id UUID PRIMARY KEY,
    operation VARCHAR(8) NOT NULL,
    facility_id UUID NOT NULL REFERENCES facilities (id),
    vehicle_identifier VARCHAR(32) NOT NULL,
    session_id UUID NOT NULL REFERENCES parking_sessions (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT parking_session_requests_operation CHECK (operation IN ('ENTER', 'EXIT')),
    CONSTRAINT parking_session_requests_vehicle_identifier_format
        CHECK (vehicle_identifier ~ '^[A-Z0-9][A-Z0-9 -]*$')
);

CREATE INDEX parking_session_requests_session_idx
    ON parking_session_requests (session_id);
