# AI Developer Workspace

A local React + TypeScript and Spring Boot platform for managing development projects and instructions.

## Current version

Phases 1–5 are implemented for the current bounded scope: dashboard, project registration/settings, tasks/history, source previews, reviewed Codex planning and isolated code candidates with dependency-free Node.js 24 checks. Original managed folders stay unchanged. Managed-project Git automation and deployment remain future milestones.

## Prerequisites

- Node.js 24 and npm
- Java JDK 21 with JAVA_HOME configured
- Git
- Internet access for the initial dependency downloads

The repository includes the Gradle Wrapper. A global Gradle installation is not needed. Scripts refresh JAVA_HOME from the machine setting when an existing terminal has not picked up the installation.

## Build and start (PowerShell)

From the repository root:

```powershell
.\scripts\Build-Workspace.ps1
$env:WORKSPACE_CODEX_ENABLED = 'false'
.\scripts\Start-Workspace.ps1
```

Open http://127.0.0.1:5173. The frontend proxies `/api` to the backend at `127.0.0.1:8080`.

After source changes, rebuild before starting:

```powershell
.\scripts\Stop-Workspace.ps1
.\scripts\Build-Workspace.ps1 -SkipInstall
.\scripts\Start-Workspace.ps1
```

The start script launches hidden background server processes and records their identities. Stop only those processes with:

```powershell
.\scripts\Stop-Workspace.ps1
```

If PowerShell execution policy blocks scripts, use the commands directly in development terminals, rather than changing the system policy:

```powershell
# Terminal 1, from backend/. Ensure JAVA_HOME is available.
.\gradlew.bat bootRun
# Terminal 2, from frontend/
npm ci
npm run dev
```

For direct backend startup from `backend/`, the default platform root is `..` and database is `backend/data/`. The supplied start script instead uses the repository-root `data/`. Use the scripts consistently, or explicitly set WORKSPACE_DATA_DIR to one absolute location before launching the backend.

## First use

1. Select **Add project** and enter an existing absolute directory path outside this repository and its ancestors.
2. Open the registered project to inspect its Git status and source files.
3. Save a task instruction. In **Instruction & plan**, choose source files, select **Review context**, inspect the exact request, and explicitly send it to Codex. The result is stored as **Plan ready**; no project files are changed.
4. Use **Settings** to save that project's conventions and instructions.
5. Use **Task history** to view instructions and cancel drafts.

Project registration never creates directories, initializes Git, installs packages, or changes project files. Existing local folders are registered rather than scaffolded in this version.

## Local data

- `data/`: H2 database used by the launch script. Back it up with servers stopped.
- `.runtime/`: server logs and process identity file.
- `.cache/gradle/`: local Gradle cache.
- The above paths and dependency/build outputs are excluded from Git.
- Never put API keys in frontend code or tracked settings.

The backend binds to loopback and rejects foreign browser origins and hosts. This is a single-user local application; it is not configured for public hosting or remote access. The UI uses Google Fonts with system font fallbacks; project contents are sent to Codex only after explicit context review and confirmation.

## Verification

```powershell
.\scripts\Build-Workspace.ps1 -SkipInstall
```

This runs the TypeScript check, Vite production build, backend integration tests, and executable JAR build. Reports are in `backend/build/reports/tests/test/index.html`.

See [progress](documentation/PROGRESS_TRACKER.md), [specification](documentation/PROJECT_SPEC.md), and [workflow](documentation/DEVELOPMENT_WORKFLOW.md).


## Jarvis interface update

The dashboard now uses a navy-and-cyan command-center layout with instruction starters and a responsive sidebar. Existing project registration, drafts, settings, history and read-only source inspection remain available. Codex planning was live-verified on the destination with a disposable TypeScript project. Planning is disabled by default and must be enabled explicitly. Execution remains unavailable. See `documentation/PROGRESS_TRACKER.md` for current verification and limitations.


## Enable read-only Codex planning (CP4)

Use your local Codex/ChatGPT sign-in; never put passwords or tokens in this repository. With existing workspace servers stopped:

```powershell
$env:WORKSPACE_CODEX_ENABLED = 'true'
.\scripts\Start-Workspace.ps1
```

The environment script discovers `codex.exe` on PATH. If it is not discoverable, set `WORKSPACE_CODEX_EXECUTABLE` to this computer's installed executable. Select **Check connection** in the dashboard. Each instruction still requires choosing source context and explicitly sending the reviewed request. Credential detection is limited; inspect the entire preview.

To include the installed-binary compatibility test in validation:

```powershell
$env:WORKSPACE_CODEX_TEST_EXECUTABLE = (Get-Command codex.exe).Source
.\scripts\Build-Workspace.ps1 -SkipInstall
```

This optional test uses a synthetic local provider, verifies that the model receives no tools, and checks that no authentication credential reaches that provider. It does not make a real model request. Ordinary lifecycle/protocol/context tests do not require Codex sign-in.

Tested App Server: `0.167.0-alpha.3.2`. Revalidate compatibility after CLI updates. See [official App Server documentation](https://learn.chatgpt.com/docs/app-server) and [current specification](documentation/PROJECT_SPEC.md).

CP6 is complete for the reviewed personal GitHub profile. Wait for user review before CP7 deployment.


## CP5 controlled candidates and checks

After reviewing a completed plan, open **Code & checks**, authorize existing/new paths, review the exact request, and request a file proposal. Review its independent candidate diff before starting checks. Failed check logs can support a separately reviewed repair request. A passing, cleaned-up result provides a downloadable patch; applying it remains a separate manual action.

Requires Docker Desktop with Linux containers and the official image prepared once:

```powershell
docker pull node:24-alpine
$env:WORKSPACE_CODEX_ENABLED = 'true'
$env:WORKSPACE_EXECUTION_ENABLED = 'true'
.\scripts\Start-Workspace.ps1
```

The fixed profile runs `node --test --test-reporter=tap` with Node.js 24. It supports dependency-free tests, copies source to a private volume, and uses read-only source/root, no network, no host mounts/credentials, an unprivileged user, 256 MB RAM, one CPU, 64 processes, bounded logs and a 60-second test limit. It does not install dependencies or accept arbitrary host commands. Limits are three candidates per task (including repairs) and three check runs per candidate. Source snapshots require a clean committed repository and supported tracked text within 250 files / 4 MB. Credential detection is heuristic; inspect every context preview.

Execution remains disabled by default. To include both runtime compatibility checks in Build-Workspace.ps1, set WORKSPACE_CODEX_TEST_EXECUTABLE and WORKSPACE_DOCKER_TEST_EXECUTABLE to the installed executables. The verified destination run passed all 47 tests with neither optional check skipped. See [progress tracker](documentation/PROGRESS_TRACKER.md) for evidence and limitations.

## Reviewed Git publishing (CP6)

Opt in with `WORKSPACE_GIT_ENABLED=true` before Start-Workspace.ps1 and sign in to the selected personal account using GitHub CLI. For a passing candidate, **Commit & GitHub** asks for the account, existing repository, author and message. Review the exact commit diff and destination, then commit locally. A separate confirmation publishes that saved commit to its dedicated `codex/task-...` branch and verifies the remote commit ID. Repository creation remains an explicit setup step outside the dashboard.

The commit preserves the original parent history and unchanged files, including files outside the bounded text snapshot. The original managed folder, index, branch and remotes are not modified. Publishing includes the original Git history: inspect it and the destination visibility before confirming. The initial profile supports personal GitHub HTTPS repositories, up to 50 MB of reachable history, and one fixed destination per committed candidate. No tokens are stored in application data, no hooks are executed, and no force push is used. A conflicting destination branch is rejected. Unconfirmed/interrupted pushes retain PUSH_UNKNOWN and can be explicitly reconciled against the exact remote branch. Private commit artifacts and publication records stay local to each laptop; push before switching computers.
