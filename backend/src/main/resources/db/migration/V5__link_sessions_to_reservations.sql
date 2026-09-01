ALTER TABLE parking_sessions
    ADD COLUMN reservation_id UUID REFERENCES reservations (id);

CREATE UNIQUE INDEX parking_sessions_reservation_unique
    ON parking_sessions (reservation_id)
    WHERE reservation_id IS NOT NULL;
