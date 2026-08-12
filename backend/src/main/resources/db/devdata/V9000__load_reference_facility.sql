INSERT INTO facilities (id, name)
VALUES ('d936bb7d-3027-47aa-a47b-d04a37e07310', 'Reference Tower');

INSERT INTO parking_floors (id, facility_id, floor_number)
SELECT
    md5('reference-floor-' || floor_number)::uuid,
    'd936bb7d-3027-47aa-a47b-d04a37e07310'::uuid,
    floor_number
FROM generate_series(1, 6) AS floors(floor_number);

INSERT INTO parking_zones (id, floor_id, code)
SELECT
    md5('reference-zone-' || floor_number || '-' || zone_code)::uuid,
    md5('reference-floor-' || floor_number)::uuid,
    zone_code
FROM generate_series(1, 6) AS floors(floor_number)
CROSS JOIN unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS zones(zone_code);

INSERT INTO parking_spaces (id, zone_id, space_number, size_class, operational_state)
SELECT
    md5('reference-space-' || floor_number || '-' || zone_code || '-' || space_number)::uuid,
    md5('reference-zone-' || floor_number || '-' || zone_code)::uuid,
    space_number,
    CASE
        WHEN space_number <= 100 THEN 'SMALL'
        WHEN space_number <= 180 THEN 'MEDIUM'
        ELSE 'LARGE'
    END,
    'ACTIVE'
FROM generate_series(1, 6) AS floors(floor_number)
CROSS JOIN unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS zones(zone_code)
CROSS JOIN generate_series(1, 200) AS spaces(space_number);
