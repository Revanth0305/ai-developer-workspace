# Project specification

## Purpose

Provide a personal, local interface that manages separate development repositories and will eventually coordinate AI planning, controlled edits, verification, Git changes, and test deployments.

## First release boundaries

One local user, one backend instance, and a React dashboard. Existing folders are registered; this platform does not yet generate managed applications. The platform source repository is distinct from managed projects.

## Implemented architecture

- Frontend: React, TypeScript, Vite. Hash navigation for overview, projects, history, and settings.
- Backend: Spring Boot, Java 21, Spring MVC and validation.
- Storage: file-backed H2 with JPA, persistent project/task/activity records.
- Integration: same-origin `/api` calls through the Vite development proxy.
- Managed project access: read-only; source text previews and fixed Git inspection commands.
- Local request guard: loopback binding, local Host/Origin checks, rejection of cross-site browser requests.

## Data

- Project: UUID, display name, canonical directory, instructions, timestamps.
- Task: UUID, project UUID, instruction, status, timestamps.
- Activity: UUID, project/task UUIDs, message, timestamp.
- Current task lifecycle: DRAFT → CANCELLED. No background AI worker consumes drafts.

## Boundaries

- Reject missing folders, relative paths, platform folders, platform ancestors, and overlapping registered directories.
- Resolve file paths against the selected project and verify their canonical location before reading.
- Skip hidden/generated directories and common credential filenames. This filename filtering is not a complete secret scanner.
- Source previews are limited to supported text formats and 64 KB per file; listings are capped at 250 files, 5,000 visited entries and 12 folder levels.
- Git inspection requires the selected folder to be the repository root, uses fixed argument arrays, limits each command to eight seconds, and caps captured output.
- No write/command-execution endpoint exists for managed projects.
- Database schema management uses Hibernate update for this prototype. Introduce versioned migrations before distributing upgrades with important user data.

## Next release: AI planning

Provider selection is deferred by the user. Once chosen:

1. Configure credentials only in the backend environment or ignored local configuration.
2. Add a provider adapter, request timeouts, usage limits, and cancellation.
3. Let the user review exactly which source context will be sent; detect/redact secrets beyond filename checks.
4. Store execution attempts and provider results separately from tasks.
5. Produce a project-specific structured plan and proposed diff; validate paths before accepting it.
6. Keep planning read-only until the controlled executor and its tests are in place.

## Later releases

Before edits: task branches/worktrees, an execution sandbox, explicit action policies, budgets, bounded logs, cancellation, retry limits, and recoverable task states. Before GitHub/deployment: user repository and hosting configuration, credential storage, reviewable diffs, real CI status and test-environment deployment results. Production deployment and unattended execution are separate later milestones.
