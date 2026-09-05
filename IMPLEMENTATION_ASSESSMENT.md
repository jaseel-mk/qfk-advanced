# QFK implementation assessment

## 1. Existing project structure

The supplied workspace contained no application source—only empty `work/` and `outputs/` folders. This is therefore a greenfield foundation; no working feature was replaced.

## 2. Existing technologies

None were present. Phase 1 establishes the requested Java 21/Spring Boot/Maven/PostgreSQL backend and React/TypeScript/Vite frontend.

## 3. Reusable features

There was no existing code to reuse. The new design system, responsive navigation, route shells, realistic sample content, exception format, security boundary, and feature-first Java packages are intended as reusable foundations for every following phase.

## 4. Features needing refactoring

The Phase 1 frontend currently uses an in-memory demonstration session and representative data so the full product shape is inspectable immediately. Phase 2 must replace those adapters with TanStack Query API hooks. Security is deny-by-default, but the JWT issue/verification/rotation filters and user-role persistence remain Phase 1 follow-on work before any public deployment.

## 5. Proposed database architecture

The organization is QFK; `teams` are independently managed children and `team_members` is a temporal many-to-many membership. Members do not require team membership. Match registration is an ordered, unique member/match record; locking the match row serializes final-slot allocation. Money is stored as `NUMERIC` with currency and will be extended with immutable double-entry-style ledger events. All operational timestamps use `timestamptz`/Java `Instant`, while Qatar-local dates remain `date`.

Planned bounded contexts: identity and roles; members; teams and membership; grounds; matches, registrations and waitlist history; match-team assignments; attendance; match events and per-match player statistics; payments and financial transactions; cash holders and transfers; tournaments and squads; polls; announcements; notifications; audit; settings.

## 6. Proposed backend architecture

Feature-first modules live below `com.qfk` (`auth`, `member`, `match`, `team`, `finance`, etc.). Each module owns its entity, repository, service, DTO, mapper and controller packages. Controllers perform transport concerns only; services own transactional rules. DTOs form the public contract and entities are never returned. Cross-cutting security, errors, auditing and time live in `common`.

## 7. Proposed frontend architecture

Public routes and authenticated application routes share brand tokens but use distinct shells. Server state belongs in TanStack Query, form state in React Hook Form/Zod, and session bootstrapping in an auth provider with an explicit `initializing` state. `ProtectedRoute` waits for initialization before choosing content or redirect, preventing the authentication flash. Authorization controls UX; backend permissions remain authoritative.

## 8. Migration plan

1. Apply Flyway foundation and import normalized members/teams.
2. Import historical matches with stable external IDs into a staging schema.
3. Reconcile identities, duplicate contacts, timezone conversion and payments.
4. Backfill per-match statistics from source history, then compare aggregates.
5. Run old and new reads in parallel, freeze legacy writes, perform final delta import.
6. Verify counts, ledger totals and sampled profiles; retain a documented rollback window.

## 9. Phase 1 implementation tasks

- [x] Establish React/Vite and Spring Boot/Maven projects.
- [x] Create premium public QFK experience and Qatar United FC distinction.
- [x] Create responsive member/admin shells and dashboard experiences.
- [x] Create a mobile-first Match Command Center representative slice.
- [x] Add PostgreSQL/Flyway foundation and multi-team memberships.
- [x] Add concurrency-safe registration service foundation.
- [x] Add baseline backend security, validation errors and OpenAPI dependency.
- [x] Add Docker, Compose, environment template and operator README.
- [ ] Complete JWT access/refresh rotation and persistent roles.
- [ ] Replace frontend demonstration adapters with API clients and auth guards.
- [ ] Add remaining Phase 1 backend endpoints, seeds and security tests.

