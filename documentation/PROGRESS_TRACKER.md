# Progress tracker

Updated: 2026-11-09 (Asia/Calcutta)

| Checkpoint | Status | Evidence |
| --- | --- | --- |
| CP1: Foundation | Complete | React/TypeScript production build; Spring Boot executable JAR; live /api/health through the frontend proxy |
| CP2: Dashboard | Complete | Browser-verified registration, project details, source preview, saved draft, settings, history and cancellation |
| CP3: Project management | Complete for read-only scope | Two-project isolation API test; canonical folder checks; overlap/platform/traversal rejection; scoped Git inspection |
| CP4: AI planning | Complete; user authorized CP5 | Reviewed context → live signed-in Codex → validated, stored project plan. 27 tests pass, including installed-binary compatibility. Live cancellation verified. |
| CP5: Code execution | In progress; Windows restart required before live Docker validation | Candidate proposals, diff review, bounded checks and repair flow implemented; container end-to-end verification is pending |
| CP6: Managed-project Git automation | Not started | Current Git integration is inspection only |
| CP7: Deployment | Not started | Local application only; no hosting target configured |
| CP8: Autonomous workflow | Not started | Requires earlier execution and recovery milestones |

## Historical CP1–3 verification

- Node.js 24.14.1, npm 11.11.0, Git 2.53.0 and Temurin JDK 21.0.13.
- React 19.3.1, Vite 8.3.6, TypeScript 7.0.2; dependencies locked in package-lock.json.
- Spring Boot 4.1.2 and Gradle Wrapper 9.8.2; distribution checksum pinned.
- `scripts/Build-Workspace.ps1 -SkipInstall`: passed frontend type checking/production build, seven backend tests, and JAR build.
- Tests: four API integration tests, two Git inspection tests, one application-context test; zero failures/errors.
- Real browser checks: project registration, README preview, draft creation, settings save, task history, cancellation, desktop and mobile layouts.
- Mobile layout measured at the tested 390-pixel viewport: no document horizontal overflow.
- Stop/start scripts exercised. Process identity records use timestamp ticks to avoid automatic JSON date conversion differences in PowerShell.
- Full server restart retained the saved project, instructions, task and CANCELLED state in file-backed H2.
- Browser reload after restart confirmed the retained cancelled task.
- Verification used `.runtime/verification-data`, separate from the user's root `data/` database.
- Browser evidence: `.runtime/verified-task-history.png` (local, ignored).

## Current limitations

- Project creation means registration of an existing folder. Managed application scaffolding is a later capability.
- Source inspection is read-only and bounded; filename exclusions are not a complete secret scanner.
- AI planning is read-only and opt-in. No managed-project editing, command runner, automatic retries, Git commit/push or deployment is available.
- This local prototype is not configured for remote access or multiple users.
- Database migrations and background execution-attempt records are deferred until the next applicable milestone.

## Next requirement

The user authorized CP5 on 2026-11-09. Resume from documentation/CP5_RESTART_HANDOFF.md after Windows restarts, finish live Docker validation, and report CP5 before proceeding to CP6. The user also requests stopping work when the remaining Codex usage quota reaches 10%. The selected platform repository is the public GitHub repository Revanth0305/ai-developer-workspace; do not change the account or repository without asking.


## Destination review and Jarvis UI — 2026-11-08

- Restored offline history and the complete transfer snapshot into the destination working folder. Migration snapshot checkpoint: aed4a4a. Original transfer package remains untouched.
- User requested the Jarvis reference as UI direction: navy surfaces, cyan actions, stronger headings, a command-first overview, CSS workspace orb, instruction starters, and responsive navigation. Existing registration, drafts, project settings, source preview and history remain connected to the local backend.
- Corrected stale provider labels: Codex is selected, but planning integration remains unfinished and has not been live-tested. CP4 is still partial; CP5–8 remain unimplemented.
- Destination tools: Git 2.56.0, Node.js 24.15.0, npm 11.13.0, Eclipse Temurin JDK/compiler 21.0.13; machine JAVA_HOME configured.
- Frontend type checking/production build and backend JAR build passed. All seven existing backend regression tests passed, zero failures/errors.
- Build environment workaround: the sandbox temporary path is too long for Java local sockets. Validation used a process-only jdk.net.unixdomain.tmpdir override to a shorter local temporary folder. No machine execution policy or security settings changed.
- Lint could not run: Windows Application Control blocked oxlint's native binding. No policy bypass was attempted for that block.
- Browser verification uses a separate ignored .runtime/ui-verification-data database and a disposable external review folder. Codex remains explicitly disabled. Desktop and 390px mobile layouts checked; no horizontal document overflow. Verified project registration, instruction starter, saved draft/history, settings status and actual source preview.
- User selected a public GitHub repository: Revanth0305/ai-developer-workspace. Public repository created and initial history/source push completed on codex/workspace-foundation; UI checkpoint a0faeea. GitHub CLI authentication confirmed the selected Revanth0305 account. Local data, caches and credentials are excluded from Git.


## CP4: reviewed Codex planning — destination, 2026-11-08

- Implemented connection checks, explicit source selection, exact redacted-context review, confirmation, asynchronous planning, stored results/attempts, task states, polling, cancellation and retry via a new reviewed request. Results show proposed paths/changes, verification steps and open questions as plain text.
- The fingerprint binds review to raw instruction/settings/source contents and the path listing. A redacted-value change still invalidates review. Bounds, excluded paths, common credential redaction and proposed-path containment are tested. Redaction remains heuristic and requires user inspection.
- Fixed worker-slot cleanup after connection creation errors, cancellation before connection creation, late-result races, queue concurrency and interrupted-attempt recovery. State transitions commit before the worker starts. Cancellation retains the active slot until its owned child process is cleaned up.
- Replaced unverified config assumptions with installed-protocol checks. Integration entries retain required transport fields and remove null config values before being disabled; inherited app/plugin/MCP capabilities and command/browser/image/delegation/hook features are disabled. Threads are ephemeral, read-only, network-disabled and never approve operations; inherited instruction sources must be empty. Unexpected tool activity fails the request.
- Installed Codex App Server 0.167.0-alpha.3.2, local ChatGPT sign-in: real browser request on a disposable TypeScript greeting project returned a stored project-specific plan. The result preserved the greeting export, proposed blank-name validation and identified missing test-runner/error-type choices. No proposed checks were represented as executed.
- Final validation: frontend type checking/production build, backend executable JAR and **27 tests**, zero failures/errors/skips in the destination run. Includes 8 planner lifecycle tests, 6 context/plan tests, 5 stdio protocol tests, 1 installed-binary compatibility test and the original 7 regression tests.
- The optional compatibility test uses a synthetic local Responses provider with authentication disabled. It verifies omitted/empty model tools and absence of an Authorization header; no real model request is made by that test. Run it by setting WORKSPACE_CODEX_TEST_EXECUTABLE before Build-Workspace.ps1. Without that variable only this installed-binary test is skipped.
- Live browser cancellation retained CANCELLED task/attempt states. Before/after SHA-256 comparison found exactly the same two fixture files and zero content differences. The original transferred data directory is untouched; live checks use .runtime/cp4-verification-data.
- Windows lint remains blocked by Application Control. No security policy was weakened. The Java long-temp-path workaround is process-scoped as documented in ENVIRONMENT_CHECK.md.
- Verification images and generated protocol schemas are ignored local artifacts under .runtime/. Provider context/results and authentication files are not published to Git.
- Stop here for user review. CP5–8 remain unimplemented; publication of the platform source does not constitute CP6 managed-project Git automation.

- Restart verification: after stopping and restarting the final backend against the same verification database, browser history retained both the PLANNED result and CANCELLED task. Reopening the planned instruction displayed its original stored result. At 390px, both document and planning-dialog widths stayed within the viewport with no horizontal overflow.


## CP5 progress and restart handoff — 2026-11-09

- User authorized CP5 and installation of Docker Desktop. The platform branch was clean and synchronized with origin before work began.
- Implemented opt-in file proposals restricted to reviewed paths, independent bounded Git candidate snapshots, diff review, fixed dependency-free Node.js 24 checks, bounded logs/time/CPU/memory/processes, cancellation, limited retries/repair proposals, patch download and restart recovery. The original managed project is not modified. No managed-project commit/push/deployment was added.
- The runner copies source into a private Docker volume, never mounts host folders or credentials, uses read-only source/root, an unprivileged UID, no network, dropped capabilities and no-new-privileges. Image IDs are pinned at review/run time. Actual Docker isolation and execution are NOT yet live-verified on this laptop.
- Added tests for candidate/source boundaries, Git clean-filter suppression, CRLF handling, subprocess output/time limits, container policy/ownership checks and execution lifecycle. The real Docker compatibility test is opt-in and must be run after the engine is available.
- Installed Docker Desktop 4.98.0 (Docker CLI 29.9.2) and Microsoft WSL 2.7.14. User completed Docker first-run setup. Administrator setup enabled VirtualMachinePlatform and explicitly reported that changes require a Windows reboot. No automatic reboot was performed.
- CP5 is not complete. Preserve the in-progress work and follow CP5_RESTART_HANDOFF.md after restart. The user requested keeping this restart process simple.

- Pre-restart checkpoint validation: frontend production build/type checking and backend JAR build passed; 46 tests passed, 0 failures, 0 errors, 1 skipped (the opt-in real Docker test). Project servers were stopped cleanly before reboot. Real Docker and browser end-to-end checks remain pending.
