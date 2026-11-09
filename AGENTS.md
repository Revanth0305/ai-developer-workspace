# Project guidance

- Read documentation/MIGRATION_HANDOFF.md and documentation/PROGRESS_TRACKER.md before continuing work.
- This is a Windows-first local React/TypeScript and Java 21 Spring Boot application.
- Keep the platform repository separate from the development projects it manages.
- Use scripts/Build-Workspace.ps1 for builds/tests; scripts/Start-Workspace.ps1 and scripts/Stop-Workspace.ps1 manage local servers.
- CP4 planning and CP5 controlled candidate execution are implemented and live-verified with a disposable project. CP5 supports only dependency-free Node.js 24 tests in isolated Linux containers, reviewed diffs/repairs and checked-patch download. Original managed folders stay unchanged. CP6 additionally supports separately reviewed private branch commits and personal GitHub HTTPS pushes. Deployment and arbitrary project builds remain unavailable.
- The Windows migration and Jarvis UI are restored. The user authorized CP6 on 2026-11-09 after CP5 completion. CP6 final verification is complete (53 tests passed). Report it and wait for review before CP7. The holographic Jarvis UI update is requested next; finish the CP6 checkpoint first. The Windows/Docker restart prerequisite is resolved; documentation/CP5_RESTART_HANDOFF.md is historical.
- User Git preference: before connecting a Git hosting account, configuring an external remote, or pushing, ask which Git account and repository to use. Do not infer an account from the local machine's existing credentials. Local history and offline Git bundles do not require a hosting account.
- Original checkpoint: 90d4d97 on codex/workspace-foundation. Migration snapshot: aed4a4a. The user subsequently selected the public GitHub repository Revanth0305/ai-developer-workspace; origin points there. Check actual Git state for current push status. Commits use Codex <codex@localhost>; no global Git identity was changed.
- Preserve all unfinished tracked and untracked source files. Build outputs, runtime processes, authentication files and dependency caches are not source.

- User quota constraint: stop work when the remaining Codex usage quota reaches 10%; check account/rateLimits/read during sustained work. Do not start the next milestone without the user reviewing the completed one.
