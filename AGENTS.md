# Project guidance

- Read documentation/MIGRATION_HANDOFF.md and documentation/PROGRESS_TRACKER.md before continuing work.
- This is a Windows-first local React/TypeScript and Java 21 Spring Boot application.
- Keep the platform repository separate from the development projects it manages.
- Use scripts/Build-Workspace.ps1 for builds/tests; scripts/Start-Workspace.ps1 and scripts/Stop-Workspace.ps1 manage local servers.
- The Codex App Server integration is unfinished. Do not describe it as live-tested or ready for use.
- The Windows migration is restored. The user requested a Jarvis-inspired UI redesign. Do not resume the unfinished planning integration or later milestones without the user requesting them.
- User Git preference: before connecting a Git hosting account, configuring an external remote, or pushing, ask which Git account and repository to use. Do not infer an account from the local machine's existing credentials. Local history and offline Git bundles do not require a hosting account.
- Original checkpoint: 90d4d97 on codex/workspace-foundation. Migration snapshot: aed4a4a. The user subsequently selected the public GitHub repository Revanth0305/ai-developer-workspace; origin points there. Check actual Git state for current push status. Commits use Codex <codex@localhost>; no global Git identity was changed.
- Preserve all unfinished tracked and untracked source files. Build outputs, runtime processes, authentication files and dependency caches are not source.
