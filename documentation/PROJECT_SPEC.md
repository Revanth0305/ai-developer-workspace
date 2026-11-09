# Project specification

## Purpose

Provide a personal local interface for separate development repositories, project-specific instructions, reviewed AI planning, and later controlled edits, verification, Git automation and test deployments.

## Implemented architecture (CP1–8)

- Frontend: React, TypeScript, Vite, a responsive SVG/CSS holographic Jarvis command center and a hover/tap navigation popover. No permanent side rail or added frontend dependencies. The overview contains only Jarvis, its command bar and quick actions; operational project/source/connection/progress/activity/instruction panels render on a separate #workspace route.
- Backend: Spring Boot / Java 21, validation and loopback Host/Origin guards.
- Storage: file-backed H2/JPA for projects, tasks, activity and separate planning, execution and Git publication records.
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
- Planning does not edit files, run checks, commit, push or deploy. CP5 separately stages validated proposals in private candidates and offers one fixed container test profile; it never writes original managed-project source.
- The application is a single-user local prototype, not a public hosting service. Hibernate schema update remains a prototype choice; versioned migrations are required before broader upgrades involving important data.

## CP5 controlled execution

CP5 is complete for dependency-free Node.js 24 candidates. A completed plan is required. The user authorizes up to eight exact source/new paths and reviews the bound request before a tool-free Codex proposal. Validated full-file writes/deletes are staged in an independent private Git snapshot; the original remains unchanged. Clean committed source is required, with supported tracked text bounded to 250 files / 4 MB. Hooks, filters and external Git helpers are disabled for snapshot inspection.

The reviewed diff, source/candidate fingerprints and resolved official image ID bind a separate explicit check run. Source is copied into a private named volume without host binds. The runner uses read-only source/root, UID 65534, no network, dropped capabilities, no-new-privileges, 256 MB RAM, one CPU, 64 processes, a 64 MB /tmp and a 60-second test limit. Only fixed `node --test --test-reporter=tap` is supported; no package installation or model-selected commands/images. Actual exit state and a positive reported test count are required for PASSED.

Execution attempts persist context, diff, bounded redacted logs, exit/test results, cancellation and cleanup state. Three candidates per task (including repairs) and three runs per candidate bound retries. A repair requires a completed failed check and separately reviewed diff/log context. Cancellation stops owned work; interrupted operations recover as INTERRUPTED. Cleanup verifies ownership, blocks further work while pending and gates successful patch export. Execution is opt-in through WORKSPACE_EXECUTION_ENABLED=true and Docker must be available; there is no host fallback.

## CP6 reviewed Git publishing

CP6 is complete for the reviewed personal GitHub profile; final validation passed all 53 tests and the real remote commit was verified. It is opt-in through WORKSPACE_GIT_ENABLED=true. Only passing, cleaned-up candidates qualify. Source and candidate fingerprints are revalidated before preview and commit. Exact account/repository, author, message, parent, branch, resulting tree and diff bind the commit review. Git plumbing builds a private tree from the real source parent and changes only validated proposal paths; original working files, index, branches and remotes remain unchanged. Original executable modes and files outside the candidate text snapshot are preserved. Reachable history is bounded to 50 MB; the private branch is repacked independently before publication.

Commit and push are separate explicit actions. The initial destination profile is an existing personal GitHub HTTPS repository; the locally signed-in CLI account must match the selected account. A fixed official CLI helper supplies credentials without storing them in the database. Push is non-force, rejects conflicting branch contents, and verifies the remote ID before reporting PUSHED. Records retain commit, branch, destination and status; interrupted or unconfirmed network writes become PUSH_UNKNOWN for explicit reconciliation, never fabricated success. An interrupted local commit is retained for review and never automatically pushed.

Pushing publishes the original repository history as well as the checked change. The UI requires reviewing destination visibility/history. Repository provisioning, original-folder application, merges, pull requests, organizations/GitLab, deployment (CP7) and unattended workflows (CP8) are outside this initial profile. Stop for user review after CP6 verification before CP7.


## CP7 local test deployment

Implemented and live-verified for committed static public/ assets with the fixed Node.js 24 Docker profile. WORKSPACE_DEPLOYMENT_ENABLED defaults false; execution and Git profiles are also required to prepare eligible candidates. Only clean passing candidates with private commits qualify. Review binds commit/tree, artifact fingerprint/manifest, image ID and localhost policy. No push is required. The trusted packaged static server imports no project server code and verifies copied bytes before listening.

The server publishes a Docker-assigned loopback port, uses bridge networking, read-only root/volume, UID 65534, no host binds, dropped capabilities and bounded resources/logs. Strict CSP requires separate scripts/styles and blocks browser external connections. No user secrets or arbitrary environment variables are accepted. There is one live deployment, three attempts per candidate, and a 30-minute server-enforced lease. Actual isolation/ownership and HTTP checks gate the saved URL. Stop/cancel, pending owned cleanup, expiry and restart recovery preserve truthful history; restart never automatically redeploys. README specifies artifact limits and operational steps. Cloud CI/CD, arbitrary application backends and hosted Jarvis remain outside this profile.


## CP8 controlled workflow profile

An opt-in WORKSPACE_WORKFLOW_ENABLED profile connects the reviewed plan/source request to proposal, fixed isolated checks and bounded repair requests. The initial review binds source/instruction/plan hash, exact existing/new paths, runtime image and a one-to-three-candidate budget. Its explicit authorization covers subsequent redacted failed-check logs and prior candidate diffs for those same paths. Existing three-candidate task limits still apply. Each generated candidate runs checks once; infrastructure failures, changed context/image, missing positive test counts or pending cleanup stop the workflow. There is one workflow worker and a 15-minute execution deadline, followed by bounded child shutdown/cleanup when needed.

Persistent workflow records retain authorization, selected paths, candidate IDs, limits, timestamps, failure details and bounded events. Cancellation prevents further stages and interrupts the owned candidate. Startup retains active runs as INTERRUPTED without automatically resuming any AI requests. Existing candidate recovery handles interrupted work and owned resources. Passing output waits for exact diff/commit review. Git push and deployment retain independent explicit confirmation; no workflow toggle grants external publication. Workflow status is derived from actual candidate/publication/deployment records, including uncertain pushes, stopped/expired deployments and failed health/cleanup. Local-only deployment without push is supported.

This is a controlled reviewed prototype profile, not unrestricted autonomous development. New repository provisioning, original-folder application/merge, arbitrary package installs/backends, production deployment and remote access remain outside it. The model receives no execution tools; all checks use the pre-existing fixed runner. Source isolation and per-project Git boundaries remain unchanged.
