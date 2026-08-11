# Documentation Standard

Documentation is maintained with the code it describes. It must distinguish current behaviour from planned work.

## Writing

- State the subject and decision directly.
- Prefer concrete nouns, commands, paths, and examples.
- Remove promotional claims that are not supported by evidence.
- Avoid decorative formatting, emojis, slogans, and conversational filler.
- Use headings to expose document structure, not to split every paragraph.
- Use lists for sets of items and procedures, not as a substitute for prose.
- Use diagrams only when they explain a relationship more clearly than text.
- Do not describe a command, endpoint, or feature as available until it works in the repository.
- Use consistent terminology from the domain model.

## Required updates

A change must update documentation when it affects:

- public API behaviour
- configuration
- data ownership or schema
- deployment and operations
- security boundaries
- setup or verification commands
- an accepted architecture decision

## Architecture decisions

Create an Architecture Decision Record for a choice that changes module boundaries, data ownership, external dependencies, deployment topology, or a system-wide engineering rule. Do not rewrite an accepted decision to hide its history. Add a new decision that supersedes it.

## Examples

Examples must be executable or labelled as illustrative. Remove stale examples instead of keeping them for appearance.

## Status language

Use these terms consistently:

- Planned: approved for future work and not yet implemented.
- In progress: implementation exists on an active branch and is not part of the default branch.
- Implemented: available on the default branch and covered by the stated verification.
- Deprecated: available but scheduled for removal with a documented replacement.
