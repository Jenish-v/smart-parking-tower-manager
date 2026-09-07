ALTER TABLE fee_adjustments
    RENAME COLUMN operator_reference TO actor_subject;

ALTER TABLE fee_adjustments
    ALTER COLUMN actor_subject TYPE VARCHAR(255);

ALTER TABLE fee_adjustments
    RENAME CONSTRAINT fee_adjustments_operator_present TO fee_adjustments_actor_present;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facilities (id),
    actor_subject VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT audit_events_actor_present CHECK (length(trim(actor_subject)) > 0),
    CONSTRAINT audit_events_action CHECK (action IN ('FEE_ADJUSTMENT_APPENDED')),
    CONSTRAINT audit_events_target_type CHECK (target_type IN ('RECEIPT'))
);

CREATE INDEX audit_events_facility_history_idx
    ON audit_events (facility_id, occurred_at DESC, id DESC);

CREATE FUNCTION reject_audit_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only';
END;
$$;

CREATE TRIGGER audit_events_reject_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();

CREATE TRIGGER audit_events_reject_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();
