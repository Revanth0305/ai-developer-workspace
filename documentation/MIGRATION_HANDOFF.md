# Windows migration handoff

Prepared: 2026-11-08 (Asia/Calcutta).

Historical transfer record: the unfinished-integration notes below describe the source computer at packaging time. Destination CP4 work supersedes those notes; see the current PROGRESS_TRACKER.md and PROJECT_SPEC.md.

## User intent

Move this complete project to another Windows computer where the user is already signed into the same Codex account. Continue development there after the user provides their requested changes. The user wants Codex itself as the AI engine and eventually wants to issue internal commands through this dashboard.

Before any external Git hosting connection or push, ask the user which Git account and repository should be used. Do not select one automatically.

## Current state

- Phases 1–3 are implemented and browser-verified: React dashboard, Spring Boot backend, H2 persistence, existing-folder registration, project settings, draft tasks, history/activity, read-only Git inspection and bounded source previews.
- Local checkpoint: 90d4d97, branch codex/workspace-foundation. No external remote or push. Phase 6 means automation of Git for managed projects; basic local version control already exists for this platform.
- After that checkpoint, the user selected Codex as the provider. A partial backend App Server adapter was added. These modifications are intentionally uncommitted and included in the transfer source snapshot.
- Latest full build passed: frontend production build, backend executable JAR and seven regression tests. The added Codex planning functionality itself has no dedicated tests yet and has NOT been live-tested.
- The running app at migration preparation used the earlier verified JAR. It is stopped before the database snapshot. Runtime processes do not transfer.
- Current docs such as README describe the earlier verified version. This handoff and the updated progress tracker take precedence for the unfinished integration state.

## Unfinished Codex files

New backend classes: CodexConnection, CodexPlanner, PlanContext, PlanAttempt, PlanAttemptRepository.

Modified: WorkspaceController, application.properties, the two existing Spring integration-test classes, and scripts/Use-ProjectEnvironment.ps1.

Intended flow: dashboard instruction → context preview → signed-in Codex App Server → read-only plan → stored result. This is not connected in the frontend yet. Do not treat any of the following as completed:

- Dashboard commands, connection display, context review and planning results.
- End-to-end Codex authentication/protocol compatibility on the destination.
- Cancellation/concurrency/restart tests for the new adapter.
- Context redaction and content-change validation tests.
- Confirmation that all inherited tools/plugins are disabled for a planning request.
- Execution isolation, code editing, Git push or deployment.

Review cancellation carefully before use: active-slot cleanup and cancellation-before-connection races need tests. Verify the installed App Server's config override format and supported schema; generated schemas from the source machine were diagnostic files, not an implementation guarantee. The source machine's installed CLI was 0.166.0-alpha.6 and reported ChatGPT authentication. The destination must authenticate locally; no credentials are transferred.

## Package contents

- project/: tracked and untracked project source, documentation, scripts and the stopped application database when present.
- repository-history.bundle: offline local Git history through the last commit.
- Restore-Workspace.ps1: restores the history and overlays the current source snapshot into a new directory.
- MANIFEST.json: SHA-256 hashes of packaged project files.
- verification/: screenshots and latest test reports, when available. Diagnostic test databases are not included.

Dependencies, caches, running-process metadata, Codex authentication, user home configuration, machine environment settings and browser storage are excluded. Registered external project folders are separate assets and are not copied by this package. If the database contains registered projects, those folders must be transferred separately and their absolute paths remapped before using them on the destination.

## Restore on the destination

1. Copy the ZIP to the other Windows computer and extract it.
2. Ensure Git, Node.js 24/npm, Java JDK 21 and PowerShell are installed. Set JAVA_HOME for JDK 21. A global Gradle installation is unnecessary.
3. From the extracted package folder run:

```powershell
.\Restore-Workspace.ps1 -Destination 'C:\Development\ai-developer-workspace'
```

The destination must not already exist. The restore script verifies source checksums, restores the offline Git history, removes the local bundle origin and copies the current files. It does not connect a Git hosting account, install dependencies or run the app.

4. Open the restored destination folder as a local project in Codex, signed into the intended account, and grant folder access there.
5. Ask Codex to read AGENTS.md and this handoff, verify the environment, and wait for the changes you want before resuming feature work.
6. When ready to validate the existing project:

```powershell
.\scripts\Build-Workspace.ps1
```

The first build downloads frontend dependencies, the pinned Gradle distribution and backend dependencies. Keep WORKSPACE_CODEX_ENABLED=false in that terminal until the unfinished integration is reviewed:

```powershell
$env:WORKSPACE_CODEX_ENABLED = 'false'
.\scripts\Start-Workspace.ps1
```

The app URL is http://127.0.0.1:5173. Stop with scripts/Stop-Workspace.ps1. If Codex is not discoverable in the destination's PATH when eventually enabling it, set WORKSPACE_CODEX_EXECUTABLE to the installed codex.exe path. Do not reuse the old computer's executable path.

## Suggested message in Codex on the destination

> This project was transferred from another Windows computer. Read AGENTS.md and documentation/MIGRATION_HANDOFF.md. Preserve the uncommitted Codex backend work. Check the environment and restored files, but wait for my requested changes before continuing feature implementation. Before connecting or pushing to external Git hosting, ask which Git account and repository I want to use.
