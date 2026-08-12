CREATE TABLE facilities (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT facilities_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE parking_floors (
    id UUID PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facilities (id) ON DELETE CASCADE,
    floor_number SMALLINT NOT NULL,
    CONSTRAINT parking_floors_number_positive CHECK (floor_number > 0),
    CONSTRAINT parking_floors_facility_number_unique UNIQUE (facility_id, floor_number)
);

CREATE INDEX parking_floors_facility_idx ON parking_floors (facility_id);

CREATE TABLE parking_zones (
    id UUID PRIMARY KEY,
    floor_id UUID NOT NULL REFERENCES parking_floors (id) ON DELETE CASCADE,
    code VARCHAR(8) NOT NULL,
    CONSTRAINT parking_zones_code_format CHECK (code ~ '^[A-Z][A-Z0-9]{0,7}$'),
    CONSTRAINT parking_zones_floor_code_unique UNIQUE (floor_id, code)
);

CREATE INDEX parking_zones_floor_idx ON parking_zones (floor_id);

CREATE TABLE parking_spaces (
    id UUID PRIMARY KEY,
    zone_id UUID NOT NULL REFERENCES parking_zones (id) ON DELETE CASCADE,
    space_number SMALLINT NOT NULL,
    size_class VARCHAR(8) NOT NULL,
    operational_state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT parking_spaces_number_positive CHECK (space_number > 0),
    CONSTRAINT parking_spaces_size_class CHECK (size_class IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT parking_spaces_operational_state CHECK (operational_state IN ('ACTIVE', 'OUT_OF_SERVICE')),
    CONSTRAINT parking_spaces_zone_number_unique UNIQUE (zone_id, space_number)
);

CREATE INDEX parking_spaces_zone_idx ON parking_spaces (zone_id);
CREATE INDEX parking_spaces_allocation_candidate_idx
    ON parking_spaces (zone_id, operational_state, size_class, space_number);
