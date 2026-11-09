# AI Developer Workspace

A local React + TypeScript and Spring Boot platform for managing development projects and instructions.

## Current version

Phases 1–8 are implemented for the current bounded scope: the holographic Jarvis dashboard, project registration/settings, tasks/history, source previews, reviewed Codex planning, isolated Node.js 24 candidates and separately reviewed personal GitHub publishing. Original managed folders stay unchanged. Reviewed local Docker static deployment and bounded controlled workflows are available.

## Holographic command center

The overview uses an animated SVG Jarvis assistant, cyan/violet glass panels, project pulse, recent instruction focus and a command bar. The navigation stays hidden: hover over the menu or left edge on desktop, or tap the menu button on mobile. It overlays the page without reserving width, and dismisses on pointer leave, outside click, Escape or a navigation choice.

Choose a project and enter an instruction; Enter saves a draft and Shift+Enter adds a line. Ctrl+K (Command+K on macOS) returns to the command bar. Plan/review starters fill the input, Continue reopens the latest saved instruction, and Build/Test/Git open the existing reviewed workflow. The overview ends after the five quick-action widgets. Active-project details, source previews, Codex connection controls, development progress, activity and saved instructions live on the separate Workspace Details page (`#workspace`), opened through Get project update, Monitor or the pop-up menu. Settings, history and publication records remain reachable through their existing controls. The top breadcrumb text is removed. Counts come from saved workspace records. Motion respects the operating system's reduced-motion preference.

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

CP7 is complete for the reviewed local Docker static profile. See the progress tracker for evidence and remaining scope.


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


## Local Docker test deployment (CP7)

Enable `WORKSPACE_EXECUTION_ENABLED=true`, `WORKSPACE_GIT_ENABLED=true` and `WORKSPACE_DEPLOYMENT_ENABLED=true` before starting with `scripts/Start-Workspace.ps1`. Deployment is disabled by default. Docker Desktop must be running with the official `node:24-alpine` image prepared. Codex is needed only for planning/proposals; deployment does not make a model request.

In **Code & checks**, use a passing, cleaned candidate, review and commit it locally, then select **Review local deployment**. Review the exact commit, artifact manifest, image ID and localhost target. Confirm the public assets before starting. A GitHub push is not required. The initial static profile serves committed `public/index.html` and supported HTML, CSS, JS/MJS, JSON and text assets, limited to 100 files, 64 KB per file and 2 MB total. Use separate scripts/styles: strict browser policy blocks inline scripts/styles and external requests. No packages, user environment variables, credentials, arbitrary servers, backend services or cloud hosting are supported. Credential detection is heuristic; inspect the public content yourself.

The trusted static server runs from a pinned image ID as an unprivileged user, with read-only root/source, no host mounts, dropped capabilities, no-new-privileges, 128 MB RAM, half a CPU and 64 processes. Docker bridge networking supports HTTP; only a Docker-assigned **127.0.0.1** port is published. Unlike candidate checks, this deployment profile does not use network=none. The trusted server imports no project server code. The dashboard reports LIVE and a URL only after ownership, isolation, copied-artifact fingerprint and actual HTTP health checks succeed.

One deployment can run at a time, with up to three attempts per candidate and a 30-minute lease. Stop/cancel and bounded logs are available in the task; Workspace Details summarizes saved results. Cleanup removes only labelled owned resources. Failed cleanup remains pending and blocks a new launch. Jarvis restart re-verifies an existing live deployment rather than redeploying it. The server enforces expiry while Jarvis is stopped; owned-resource cleanup resumes when Jarvis is running. Deployment volumes, database records and private commits remain local to each laptop. CP8 requires separate user review.


## Controlled workflows (CP8)

Opt in with `WORKSPACE_WORKFLOW_ENABLED=true` along with the Codex and execution flags. Start with a stored reviewed plan, authorize exact file paths in Code & checks, and review the change context. Select a budget of one to three candidates (within the task's existing three-candidate limit), then **Review controlled workflow**. The separate preview binds the exact source/plan context, fixed Docker image and budget. Confirm the authorization before starting.

The workflow requests a tool-free proposal, checks its validated candidate in the existing isolated Node.js 24 runner, and can request bounded repairs for confirmed nonzero test failures. That initial authorization explicitly permits subsequent redacted failed-check logs and prior diffs to be sent to Codex for the same paths. Those later contexts are retained with each candidate. Source/plan changes or runtime-image changes stop the loop. Infrastructure failures, missing test counts and pending cleanup stop without automatic retry. Budget is at most three model proposals and one check per candidate, within a 15-minute execution deadline; cancellation and cleanup may take the existing bounded subprocess shutdown time.

After tests pass, open **workflow candidate & next steps** to review its actual diff. Commit, GitHub push and local deployment retain their separate exact reviews and confirmations. Workflow status follows the real candidate, publication and deployment records; a local-only deployment is also permitted without pushing. A completed deployment later stopped/expired is shown accordingly, not indefinitely successful. Closing the dialog does not cancel an authorized running loop. Cancel it with **Cancel controlled workflow**; inspect pending cleanup in candidate history. Restart records an active loop as INTERRUPTED and never resumes AI requests or publishing automatically. No original folder is modified, no new repositories are provisioned, and no unattended external publication is authorized by this switch.


## Same-Wi-Fi UI preview

Use `scripts/Start-UI-Preview.ps1` (optionally `-BindAddress <this laptop's private Wi-Fi IPv4>`). It builds a separate preview under ignored `.runtime/ui-preview/dist` with `VITE_UI_PREVIEW=true`, then serves it on that Wi-Fi address at port 4173. Open the printed HTTP URL on a phone connected to the same Wi-Fi. Keep the laptop awake and the preview running. The address may change after reconnecting Wi-Fi; rerun the script to discover the current one.

This is the same interface with explicitly labelled sample data. Typing, navigation and dialogs can be inspected; saving and development actions are disabled. The standalone static server has no backend proxy, returns only allowlisted sample API responses, rejects all non-GET/HEAD requests and serves only its built assets. No local project folders, data records, credentials or Java backend are exposed. The normal frontend/backend stay on loopback ports 5173/8080. Do not point a LAN-facing Vite server at the normal backend.

Stop with `scripts/Stop-UI-Preview.ps1`; it verifies the recorded process identity before stopping it. This preview requires Node.js 24 and the frontend dependencies, not the Java server. Existing firewall/router settings must permit the phone to reach the chosen address and TCP 4173; guest Wi-Fi isolation or VPNs can prevent access. The scripts do not disable the firewall or change network profiles. This is a temporary local review link, not public hosting.
