# AI Developer Workspace

A local React + TypeScript and Spring Boot platform for managing development projects and instructions.

## Current version

Phases 1–3 are implemented: dashboard, persistent project registration, per-project instructions, draft tasks, task history, activity, read-only Git status, and bounded source previews. AI integration and execution are not enabled. The AI provider will be chosen later.

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
3. Save a task instruction. It remains **Draft** until AI integration is available.
4. Use **Settings** to save that project's conventions and instructions.
5. Use **Task history** to view instructions and cancel drafts.

Project registration never creates directories, initializes Git, installs packages, or changes project files. Existing local folders are registered rather than scaffolded in this version.

## Local data

- `data/`: H2 database used by the launch script. Back it up with servers stopped.
- `.runtime/`: server logs and process identity file.
- `.cache/gradle/`: local Gradle cache.
- The above paths and dependency/build outputs are excluded from Git.
- Never put API keys in frontend code or tracked settings.

The backend binds to loopback and rejects foreign browser origins and hosts. This is a single-user local application; it is not configured for public hosting or remote access. The UI uses Google Fonts with system font fallbacks; project content is not sent to an AI provider.

## Verification

```powershell
.\scripts\Build-Workspace.ps1 -SkipInstall
```

This runs the TypeScript check, Vite production build, backend integration tests, and executable JAR build. Reports are in `backend/build/reports/tests/test/index.html`.

See [progress](documentation/PROGRESS_TRACKER.md), [specification](documentation/PROJECT_SPEC.md), and [workflow](documentation/DEVELOPMENT_WORKFLOW.md).
