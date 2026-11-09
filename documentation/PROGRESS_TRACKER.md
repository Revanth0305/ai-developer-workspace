# Progress tracker

Updated: 2026-11-09 (Asia/Calcutta)

| Checkpoint | Status | Evidence |
| --- | --- | --- |
| CP1: Foundation | Complete | React/TypeScript production build; Spring Boot executable JAR; live /api/health through the frontend proxy |
| CP2: Dashboard | Complete | Browser-verified registration, project details, source preview, saved draft, settings, history and cancellation |
| CP3: Project management | Complete for read-only scope | Two-project isolation API test; canonical folder checks; overlap/platform/traversal rejection; scoped Git inspection |
| CP4: AI planning | Complete; user authorized CP5 | Reviewed context → live signed-in Codex → validated, stored project plan. 27 tests pass, including installed-binary compatibility. Live cancellation verified. |
| CP5: Code execution | Complete for controlled Node.js 24 candidate scope; awaiting user review | 47 tests passed, including real Docker isolation; live failed check → reviewed repair → 2 passing tests, downloaded patch and cancellation; original source unchanged |
| CP6: Managed-project Git automation | Complete for reviewed personal GitHub profile; awaiting user review | Final transport-fix build: 53 tests pass, zero failures/errors/skips. Real commit and GitHub push verified; original source unchanged |
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
- Planning is read-only. Candidate checks and private Git commits/pushes are separate opt-in reviewed operations. Original-folder editing, arbitrary command execution and deployment remain unavailable.
- This local prototype is not configured for remote access or multiple users.
- Attempts and publications persist in H2. Versioned migrations remain deferred for broader upgrades.

## Next requirement

CP6 final verification is complete. Report and stop before CP7. The user requested the holographic Jarvis UI next, after finishing this checkpoint. Stop work when remaining Codex usage quota reaches 10%. The platform repository remains Revanth0305/ai-developer-workspace. The user separately authorized public Revanth0305/ai-workspace-cp6-verification for the disposable managed-project verification, and restored GitHub CLI device sign-in. Do not infer new managed-project destinations from either repository. The restart handoff below is historical and resolved.


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

## CP5 completed after restart — 2026-11-09

- Windows restarted, the hypervisor is active, and Docker Desktop's Linux engine is healthy. Official node:24-alpine image resolved to sha256:a34ab6d7f6f4813f4d0c5469d74685604b962fbdbf5793a7bc7b4998ecb867c8. Runs bind the reviewed image ID.
- Build-Workspace.ps1 -SkipInstall passed frontend type checking/production build, backend executable JAR and **47 tests, zero failures/errors/skips**, with both installed Codex and real Docker compatibility opt-ins enabled. Real Docker verifies read-only source/root, no external network interface or host credentials/socket, passing/failing tests and cleanup. Runtime opt-in checks now bypass cached skipped test results.
- Fixed a Windows Codex launcher cleanup race by waiting for owned child termination. Detached Vite uses child-only CI=true so closed launcher stdin does not terminate the frontend. No machine security policy was weakened.
- Browser verification used a separate ignored database and a clean disposable greeting repository. Real Codex produced a stored plan and a comment-only initial candidate, intentionally leaving the blank-name test failing. One initial bounded check timed out with no confirmed exit/test count and cleaned up; a reviewed retry reported exit 1, two tests, one failure. This timeout was not counted as a test result.
- Reviewed the actual failed logs and prior diff before requesting the repair. The repaired candidate reported exit 0, two tests, zero failures. Browser patch download succeeded; git apply --check accepted the downloaded patch without applying it. Only greeting.mjs was authorized; tests stayed outside editable paths.
- Third candidate exercised cancellation after checks started: stored CANCELLED, cleanupPending=false. Docker lists no owned leftover containers or volumes. The task visibly enforces the three-candidate limit. All three original source hashes match their baseline and the original Git working tree remains clean.
- After a normal Stop-Workspace/Start-Workspace cycle with the same verification database, browser history retained FAILED, PASSED and CANCELLED candidates, the repaired diff, actual exit/test counts and the checked-patch link. Desktop 1280px and mobile 390px document widths matched their viewports with no horizontal overflow. Final frontend production build/type checking passed after updating the journey to 5/8. Proof images remain ignored under .runtime/.
- Scope: independent private Git candidates, exact context/diff review, fixed dependency-free Node.js 24 checks, bounded logs/retries/repairs, cancellation, persisted results and checked-patch export. No original-source application, managed-project commit/push, dependency installation, arbitrary host commands or deployment. Execution stays opt-in.
- CP6–8 remain unstarted. Stop for user review before CP6.

## CP6 verification — 2026-11-09

- User authorized CP6 and separately approved public Revanth0305/ai-workspace-cp6-verification for disposable managed-project verification. GitHub CLI device sign-in was restored as Revanth0305. The platform repository remains Revanth0305/ai-developer-workspace.
- Implemented opt-in WORKSPACE_GIT_ENABLED, exact account/repository and author/message review, private full-tree branch commits from checked candidates, preserved original parents/modes/unchanged binary files, independent object storage, separate push confirmation and exact remote-ID verification. No original working-folder/index/branch/remote writes, hooks, force pushes, model-controlled Git commands or stored credentials.
- Six new CP6 tests cover parent/binary/source preservation, stale reviews, dirty/tampered source, failed/unclean candidates, unauthorized paths, idempotent push, conflicting remote branches, interrupted push recovery and invalid destination/metadata. Expanded build passed **53 tests, zero failures/skips**, with real Docker and installed Codex checks enabled before the final transport correction.
- Browser verified real candidate commit **7ce3f8c4e3efa3fa30d43edda70ee8c4ec5c3213**, parent b4f8ebd77d751913acb18e4d83941fdf8aa837e4, dedicated branch codex/task-7eff31ec-47eb581f, in the approved verification repository. GitHub API independently confirmed the exact branch ID. Commit changes only greeting.mjs (four added lines). All three original source hashes and original clean Git status are unchanged.
- Live Windows transport initially failed because Java/Git split a double-quoted helper path containing spaces. The application retained PUSH_UNKNOWN, including across restart. A credential-safe Java dry run reproduced the failure and verified shell single quoting. After correction, explicit browser reconciliation/push reached PUSHED with the real GitHub commit link. No false push success was reported.
- Mobile document width remained 390 at a 390px viewport. Browser proof is ignored .runtime/cp6-pushed.png. Final transport-fix full build was started; inspect its latest test XML/report before declaring CP6 fully complete. The journey remains 5/8 until that final confirmation is saved. Stop before CP7, and stop at the user's 10% quota threshold. Last checked quota: 11% primary, 71% secondary.
- Limits: existing personal GitHub HTTPS repositories, 50 MB reachable history, one bound destination per committed candidate. Push includes original repository history; the UI explicitly requires reviewing history and visibility. Repository creation, original-folder application/merge, organizations/GitLab and deployment remain outside this initial profile. Local publication artifacts/data/credentials do not synchronize through Git.

## CP6 final confirmation - 2026-11-09

- Resumed after primary quota reset (100% primary / 71% secondary). The final transport-fix run passed all **53 tests, zero failures/errors/skips**, including installed Codex and real Docker compatibility. Reports completed after the credential-helper correction; no backend changes or repeated tests were needed.
- GitHub independently reconfirmed branch codex/task-7eff31ec-47eb581f at **7ce3f8c4e3efa3fa30d43edda70ee8c4ec5c3213**. The retained verification database reports PUSHED with that exact commit and branch. Platform Git state was clean and synchronized before finalizing.
- This confirmation supersedes the paused final-build notes above. Dashboard journey updated to **6/8**, with deployment awaiting user review. The holographic UI redesign is pending. No CP7 work was started.
