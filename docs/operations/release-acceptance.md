# Release Acceptance

The release acceptance workflow proves that a fresh local stack supports the integrated business path. Start the stack
with `docker compose up --detach --build --wait`, then run:

```bash
scripts/verify-release-acceptance.sh
```

The script uses unique identifiers and verifies:

- capacity snapshot and deterministic allocation
- reservation creation, replay, fulfillment, and cancellation
- parking entry, active lookup, exit, and stable idempotency conflicts
- occupancy changes and release
- immutable receipt creation and append-only adjustment
- verified local actor attribution and audit history

The workflow is safe to repeat against the maintained local fixture, although it retains completed acceptance records.
Runtime CI runs it against a disposable database before recovery and shutdown drills.

Local mode intentionally disables OIDC so the workflow has no external identity-provider dependency. Backend security
tests separately verify token validation and role boundaries. A selected production environment still requires its own
identity, ingress, backup, rollback, and capacity acceptance evidence.
