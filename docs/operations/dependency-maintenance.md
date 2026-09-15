# Dependency Maintenance

Dependency changes use the same review and verification path as application changes. Dependabot opens weekly pull
requests for Maven, npm, container images, and GitHub Actions. A generated pull request is a proposal, not approval.

## Supported release line

The current release candidate is built and tested with Java 21 and Node.js 22. Runtime-image updates must preserve
those major versions unless a planned platform migration changes the documented support policy. Framework and build
tool major upgrades are handled as dedicated engineering work because they can change application, plugin, or runtime
contracts.

Patch and minor updates may be grouped when their combined branch passes every affected check. Major updates require a
compatibility review, migration notes, and the complete relevant verification suite. Failed generated updates are
closed rather than carried indefinitely; a replacement may be opened when the incompatibility is resolved.

## Required verification

Use the smallest complete set for the affected surface:

- Maven or backend-image changes: Backend CI, Security CI, and Local Runtime CI.
- npm or frontend-image changes: Frontend CI, Security CI, and Local Runtime CI.
- GitHub Actions changes: every workflow that uses the changed action, plus Security CI.
- Cross-cutting or grouped changes: Backend CI, Frontend CI, Security CI, and Local Runtime CI.

Security findings take priority over the regular update schedule. Do not bypass a failed check to merge a version
change. Record a temporary exception in the security review with the affected component, exposure, mitigation, owner,
and review date.

## Release review

Before publishing a release candidate:

1. Review every open dependency pull request and security alert.
2. Merge compatible updates only after current-branch checks pass.
3. Close unsupported or failed proposals with the reason recorded on the pull request.
4. Run the integrated release-acceptance workflow on the resulting `main` commit.
5. Record shipped dependency versions in the release tag rather than maintaining a second inventory by hand.
