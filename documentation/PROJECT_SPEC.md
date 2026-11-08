# Project specification

## Purpose

Provide a personal local interface for separate development repositories, project-specific instructions, reviewed AI planning, and later controlled edits, verification, Git automation and test deployments.

## Implemented architecture (CP1–4)

- Frontend: React, TypeScript, Vite, Jarvis command-center styling and responsive navigation.
- Backend: Spring Boot / Java 21, validation and loopback Host/Origin guards.
- Storage: file-backed H2/JPA for projects, tasks, activity and separate planning attempts.
- Source access: bounded read-only listing and previews. Managed folders remain outside the platform repository.
- Planning: a locally signed-in Codex App Server child process per request, JSON-RPC over stdio, ephemeral threads, structured output and validated relative proposed paths.
- Codex is opt-in through `WORKSPACE_CODEX_ENABLED=true`; default startup remains disabled.

## Data and lifecycle

Project records contain an independent canonical directory and conventions. Tasks start as DRAFT. Planning transitions DRAFT/FAILED/PLANNED → PLANNING → PLANNED or FAILED. Draft and running requests can be CANCELLED. Every provider attempt stores its redacted context, result/error, timestamps and optional thread ID separately from its task. Restart recovery marks unfinished attempts INTERRUPTED and affected tasks FAILED. Completed and cancelled results are preserved.

## Reviewed planning flow

1. Save an instruction for the selected project.
2. Choose at most eight source files; no contents are selected automatically.
3. Review the exact redacted request, including the instruction, project conventions, bounded source path list and selected contents.
4. Explicitly send that reviewed context through the locally signed-in Codex account. A raw-content fingerprint rejects stale reviews, including changes hidden by redaction.
5. Review the stored goal, proposed file changes, later verification steps and open questions. Closing the dialog does not cancel work; the task can be reopened from history. Cancellation stops the owned child process.

Limits: 12,000 characters per source file, 24,000 selected source characters, 48,000 request characters and 64,000 result characters. Planning defaults to 240 seconds, configurable within 30–600 seconds. Only one planning worker can run or stop at a time.

## Boundaries

- Reject missing/relative folders, platform folders and ancestors, and overlapping registrations.
- Resolve source paths canonically; reject traversal and paths escaping the registered project, including existing symlink ancestors of proposed new paths.
- Exclude hidden/generated folders and common credential filenames. Credential-pattern redaction covers common tokens, assignments and private keys, but is not a complete secret scanner. Users must inspect the preview.
- Source previews remain limited to 64 KB, 250 files, 5,000 visited entries and 12 folder levels.
- The planning process disables shell, apps, plugins, MCP servers, delegation, browser/computer access, image tools, hooks and related inherited capabilities. It verifies read-only/no-network/never-approve thread settings and empty instruction sources. Installed-binary compatibility testing verifies an empty model-facing tool catalog against a local synthetic provider.
- Unexpected tool activity or approvals fail the request. Provider errors are reduced to local actionable messages rather than exposing raw account diagnostics. Model results render as plain text.
- Planning does not edit files, run checks, commit, push or deploy. No managed-project write or command-execution endpoint is available.
- The application is a single-user local prototype, not a public hosting service. Hibernate schema update remains a prototype choice; versioned migrations are required before broader upgrades involving important data.

## Next milestone: CP5 controlled execution

Wait for the user's CP4 review before starting. CP5 requires an isolated executor, reviewed diffs, task branches/worktrees, bounded commands/logs/retries, cancellation and recoverable state. Managed-project Git automation (CP6), deployment (CP7) and unattended workflows (CP8) remain separate milestones. Publishing this platform's source repository does not implement CP6.
