# ADR-0001: Use a modular monolith

Date: 2026-08-11

Status: Accepted

## Context

The system needs strong transactional correctness for allocation, parking sessions, reservations, and pricing. The first deployment serves one parking facility with 7,200 spaces. The project also needs clear ownership boundaries and a path to scale without accepting unnecessary distributed-system failure modes at the start.

A single unstructured application would make those boundaries difficult to maintain. Independent services would introduce network calls, distributed transactions, deployment coordination, and additional operational work before those costs solve a measured problem.

## Decision

Build the backend as one deployable Java application divided into domain modules.

Each module:

- owns its domain model and persistence tables
- exposes behaviour through explicit application interfaces
- does not import another module's internal implementation
- publishes domain events for cross-module reactions where immediate coupling is unnecessary
- includes tests that exercise its public boundary

The browser application remains a separate deployable client. PostgreSQL is the shared database server, but table ownership follows module boundaries.

## Consequences

Transactions can span allocation and parking-session changes without distributed coordination. Local development and deployment remain straightforward. Module contracts must still be enforced through package structure, tests, and review because process boundaries do not provide isolation.

A module may be extracted into a service later when measured scale, availability, deployment cadence, or team ownership justifies it. Extraction is not scheduled as roadmap work by default.

## Alternatives considered

### Unstructured monolith

Rejected because it lowers the initial setup cost but provides no durable ownership or dependency rules.

### Microservices

Rejected for the initial release because the expected load does not justify distributed transactions, message infrastructure, and independent operations.

### Serverless functions

Rejected as the primary architecture because allocation and session workflows need predictable transactional behaviour and cohesive domain boundaries.
