# Project guidance

- Read documentation/MIGRATION_HANDOFF.md and documentation/PROGRESS_TRACKER.md before continuing work.
- This is a Windows-first local React/TypeScript and Java 21 Spring Boot application.
- Keep the platform repository separate from the development projects it manages.
- Use scripts/Build-Workspace.ps1 for builds/tests; scripts/Start-Workspace.ps1 and scripts/Stop-Workspace.ps1 manage local servers.
- The Codex App Server integration is unfinished. Do not describe it as live-tested or ready for use.
- The user requested a migration to another Windows computer. Do not resume feature implementation until the user asks to continue.
- User Git preference: before connecting a Git hosting account, configuring an external remote, or pushing, ask which Git account and repository to use. Do not infer an account from the local machine's existing credentials. Local history and offline Git bundles do not require a hosting account.
- No external Git remote has been configured or pushed. The local checkpoint is 90d4d97 on codex/workspace-foundation, authored by Codex <codex@localhost>; no global Git identity was changed.
- Preserve all unfinished tracked and untracked source files. Build outputs, runtime processes, authentication files and dependency caches are not source.
