# CP5 restart handoff

Prepared: 2026-11-09 (Asia/Calcutta).

## Immediate next step

Restart Windows normally. Docker Desktop and WSL are installed; VirtualMachinePlatform was enabled successfully, but Microsoft setup reported that it cannot take effect until Windows restarts. The user agreed to the required restart. Do not add setup workarounds or claim Docker checks have passed before the reboot.

After restart, the user can reopen this project in Codex and say: **Continue CP5 after the restart.**

## Preserve and resume

- Read AGENTS.md and documentation/PROGRESS_TRACKER.md, inspect Git status, and check remaining Codex quota. Stop at 10% remaining. Keep the already selected public GitHub repository Revanth0305/ai-developer-workspace.
- CP4 is complete. The user authorized CP5; do not ask them to approve CP4 again. Stop and report when CP5 is verified, before starting CP6.
- CP5 changes implement a candidate-only workflow. Codex stays tool-free and returns structured file proposals; the backend validates authorized paths, creates an independent bounded Git snapshot, stages changes, displays the diff, and requires a separate reviewed check run. Original managed folders are not modified.
- Initial check profile: dependency-free Node.js 24, `node --test --test-reporter=tap`, fixed Linux container. No arbitrary host commands or automatic dependency installation. No managed-project commits, pushes or deployment. Passing patches can be downloaded for review.
- Execution attempts and reviewed context/results/logs are stored separately. Limits: 3 candidate proposals per task (including repairs), 3 check runs per candidate, 60 seconds per container check, 256 MB RAM, 1 CPU, 64 processes, a 64 MB temporary filesystem and bounded logs. Common credential redaction is heuristic; repair requests require context review before sending logs to Codex.
- Candidate snapshots accept clean committed Git roots and supported tracked text only, with 250 files / 4 MB bounds. Git hooks, fsmonitor, external diff/textconv and configured clean/smudge/process filters are disabled for inspection. CRLF content and existing unfinished changes must be preserved.

## Verify after restart

1. Start Docker Desktop and verify its `desktop-linux` engine is available. Use the installed CLI at `C:\Program Files\Docker\Docker\resources\bin\docker.exe` if PATH has not refreshed.
2. Pull the official `node:24-alpine` image once. Do not run model-provided commands on the host. Checks use the resolved image ID, not a mutable model-selected image.
3. Run scripts/Build-Workspace.ps1 with WORKSPACE_CODEX_TEST_EXECUTABLE set to the installed codex.exe and WORKSPACE_DOCKER_TEST_EXECUTABLE set to docker.exe. The real Docker compatibility test must pass, including read-only source/root, no external interface/host credentials, real passing/failing checks and owned-resource cleanup. Resolve actual Docker compatibility issues rather than relaxing isolation.
4. Start with WORKSPACE_CODEX_ENABLED=true and WORKSPACE_EXECUTION_ENABLED=true using a separate .runtime/cp5-verification-data database. Scripts discover Codex/Docker paths; use scripts/Start-Workspace.ps1 and Stop-Workspace.ps1.
5. Browser-verify: create a disposable clean committed Node.js fixture outside the platform, obtain a CP4 plan, open **Code & checks**, review authorized source/new paths, request a real file proposal, review the diff, run actual container checks and download the checked patch. Compare original source hashes before/after. Verify a failed check plus a reviewed bounded repair, cancellation and restart persistence. Keep all verification data ignored.
6. Update documentation and milestone state only after real checks pass. Commit/push the verified CP5 checkpoint, report it to the user and wait before CP6.

## Environment notes

- Docker Desktop first-run agreement/setup was completed by the user. Docker CLI is installed, but its engine returned unavailable before reboot. WSL reported the missing/inactive VirtualMachinePlatform prerequisite; administrator setup enabled it and requested restart.
- Java builds in this environment need the existing process-only shorter jdk.net.unixdomain.tmpdir workaround. Do not weaken Windows security policies. Windows Application Control previously blocked oxlint; frontend type checking/build are separate checks.
- Local databases, dependency caches, running processes, Docker images/volumes and credentials are not Git-synchronized. Original transferred data remains untouched.

## Pre-restart validation result

Build-Workspace.ps1 -SkipInstall passed: frontend type checking/production build, backend executable JAR and 46 tests. The real Docker compatibility test was skipped because the engine cannot start before reboot. No container success is claimed. Project servers were stopped cleanly. Resume with the actual engine/image and browser checks listed above.
