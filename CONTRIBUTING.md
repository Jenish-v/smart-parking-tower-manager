# Contributing

Changes should be small enough to review, complete enough to verify, and tied to a documented requirement or defect.

## Before starting

1. Check the roadmap and open issues for overlapping work.
2. Record material architecture changes as an Architecture Decision Record.
3. Confirm which module owns the data and behaviour being changed.
4. Keep planned features separate from implemented features in documentation.

## Branches and commits

Use short branch names that describe the work:

```text
feature/allocation-engine
fix/duplicate-active-session
docs/operator-runbook
```

Commit messages use an imperative subject and describe one coherent change. Do not combine formatting cleanup with functional changes unless the cleanup is required for the change.

## Implementation rules

- Keep domain rules independent of web and persistence frameworks.
- Validate input at system boundaries.
- Treat database constraints as part of the correctness model.
- Add tests for new behaviour and regression tests for defects.
- Avoid introducing a service, dependency, or abstraction without a current use.
- Do not commit credentials, local environment files, generated output, or editor state.
- Update the relevant documentation in the same change as the behaviour.

## Verification

Each component README owns its working build and test commands. Run the commands for every component touched by a change. Pull requests must state what was run and disclose checks that could not be completed.

## Pull requests

A pull request should explain:

- the problem or requirement
- the implemented behaviour
- significant design choices
- data or compatibility impact
- verification performed
- follow-up work intentionally left out

Screenshots belong in user-interface changes. API changes require an updated contract or example.

## Review

Reviewers check correctness, concurrency, data integrity, security, operational impact, tests, and documentation. A passing build does not replace engineering review.
