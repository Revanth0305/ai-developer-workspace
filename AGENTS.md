# Project guidance

- Read documentation/MIGRATION_HANDOFF.md and documentation/PROGRESS_TRACKER.md before continuing work.
- This is a Windows-first local React/TypeScript and Java 21 Spring Boot application.
- Keep the platform repository separate from the development projects it manages.
- Use scripts/Build-Workspace.ps1 for builds/tests; scripts/Start-Workspace.ps1 and scripts/Stop-Workspace.ps1 manage local servers.
- CP4 read-only Codex planning is implemented and live-verified on the destination with a disposable project. Check the current progress tracker for evidence and limits. Never describe CP5 execution, managed-project push or deployment as available.
- The Windows migration and Jarvis UI are restored. The user authorized finishing CP4 and explicitly wants to review each milestone before continuing. The user authorized CP5 on 2026-11-09. Finish and report CP5, then wait for review before CP6. Windows setup now requires a restart; read documentation/CP5_RESTART_HANDOFF.md before resuming.
- User Git preference: before connecting a Git hosting account, configuring an external remote, or pushing, ask which Git account and repository to use. Do not infer an account from the local machine's existing credentials. Local history and offline Git bundles do not require a hosting account.
- Original checkpoint: 90d4d97 on codex/workspace-foundation. Migration snapshot: aed4a4a. The user subsequently selected the public GitHub repository Revanth0305/ai-developer-workspace; origin points there. Check actual Git state for current push status. Commits use Codex <codex@localhost>; no global Git identity was changed.
- Preserve all unfinished tracked and untracked source files. Build outputs, runtime processes, authentication files and dependency caches are not source.

- User quota constraint: stop work when the remaining Codex usage quota reaches 10%; check account/rateLimits/read during sustained work. Do not start the next milestone without the user reviewing the completed one.
