# Audit History

## Scope

Audit history records successful sensitive commands, not ordinary reads or failed attempts. The current sensitive
command is an administrator fee adjustment. Authentication and authorization failures belong in security logs and
metrics rather than the durable business audit table.

Each event stores an opaque UUID, facility, authenticated actor subject, controlled action, target type, target UUID,
and UTC occurrence time. The actor subject comes from the validated access token and is not accepted in the request.
The local profile uses the explicit `local-development` subject because authentication is intentionally disabled.

## Consistency and immutability

The pricing adapter inserts the fee adjustment and `FEE_ADJUSTMENT_APPENDED` event in the same Spring transaction.
Failure to persist either record rolls both back. Replaying an existing adjustment returns the original statement and
does not create a second event.

Application code exposes no update or delete port for audit events. PostgreSQL triggers also reject direct updates and
deletes, protecting the invariant from maintenance code and accidental SQL. Database administrators still control the
physical database; operational access and backup retention must preserve that trust boundary.

## Retrieval

`GET /api/v1/facilities/{facilityId}/audit-events` requires `ADMIN`. Results are ordered by occurrence time and UUID,
newest first. `before` is an exclusive timestamp cursor and `limit` accepts 1 through 500, with 100 as the default.
Facility scoping is mandatory at the API and query boundaries.

Audit history is retained indefinitely until a production retention policy is approved. Export, archival, actor
display-name resolution, and audit coverage for future administrator commands are operations work, not implemented
features.
