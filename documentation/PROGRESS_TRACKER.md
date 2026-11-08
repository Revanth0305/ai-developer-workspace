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
