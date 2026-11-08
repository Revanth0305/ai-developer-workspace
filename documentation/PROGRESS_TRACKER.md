# Progress tracker

Updated: 2026-11-08 (Asia/Calcutta)

| Checkpoint | Status | Evidence |
| --- | --- | --- |
| CP1: Foundation | Complete | React/TypeScript production build; Spring Boot executable JAR; live /api/health through the frontend proxy |
| CP2: Dashboard | Complete | Browser-verified registration, project details, source preview, saved draft, settings, history and cancellation |
| CP3: Project management | Complete for read-only scope | Two-project isolation API test; canonical folder checks; overlap/platform/traversal rejection; scoped Git inspection |
| CP4: AI planning | Codex selected; backend partially implemented | Latest build/regression tests pass; frontend wiring and live Codex verification are pending. Work paused for user changes and Windows migration. |
| CP5: Code execution | Not started | Requires isolated executor and a selected provider |
| CP6: Managed-project Git automation | Not started | Current Git integration is inspection only |
| CP7: Deployment | Not started | Local application only; no hosting target configured |
| CP8: Autonomous workflow | Not started | Requires earlier execution and recovery milestones |

## Verified

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
- No AI planning, file editing, arbitrary command runner, automatic retries, Git commit/push, or deployment is presented as available.
- This local prototype is not configured for remote access or multiple users.
- Database migrations and background execution-attempt records are deferred until the next applicable milestone.

## Next requirement

Restore on the destination Windows computer and read MIGRATION_HANDOFF.md. The user selected signed-in Codex rather than a separate API-key provider. Wait for the user's changes before continuing the unfinished integration. Ask which Git hosting account and repository to use before any external connection or push.


## Destination review and Jarvis UI — 2026-11-08

- Restored offline history and the complete transfer snapshot into the destination working folder. Migration snapshot checkpoint: aed4a4a. Original transfer package remains untouched.
- User requested the Jarvis reference as UI direction: navy surfaces, cyan actions, stronger headings, a command-first overview, CSS workspace orb, instruction starters, and responsive navigation. Existing registration, drafts, project settings, source preview and history remain connected to the local backend.
- Corrected stale provider labels: Codex is selected, but planning integration remains unfinished and has not been live-tested. CP4 is still partial; CP5–8 remain unimplemented.
- Destination tools: Git 2.56.0, Node.js 24.15.0, npm 11.13.0, Eclipse Temurin JDK/compiler 21.0.13; machine JAVA_HOME configured.
- Frontend type checking/production build and backend JAR build passed. All seven existing backend regression tests passed, zero failures/errors.
- Build environment workaround: the sandbox temporary path is too long for Java local sockets. Validation used a process-only jdk.net.unixdomain.tmpdir override to a shorter local temporary folder. No machine execution policy or security settings changed.
- Lint could not run: Windows Application Control blocked oxlint's native binding. No policy bypass was attempted for that block.
- Browser verification uses a separate ignored .runtime/ui-verification-data database and a disposable external review folder. Codex remains explicitly disabled. Desktop and 390px mobile layouts checked; no horizontal document overflow. Verified project registration, instruction starter, saved draft/history, settings status and actual source preview.
- User selected a public GitHub repository: Revanth0305/ai-developer-workspace. Repository created; initial push authentication pending at this checkpoint. Local data, caches and credentials are excluded from Git.
