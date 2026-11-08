# Development workflow

Build → Test → Verify → Commit → Continue.

1. Keep this platform's source separate from managed project folders.
2. Make a small, reviewable change.
3. Run the relevant backend tests and frontend production build.
4. Verify integration and user-visible behavior when changed.
5. Inspect the diff and exclude local data, build output and credentials.
6. Commit the verified checkpoint.
7. Update the progress tracker with evidence and remaining requirements.

## Checkpoint expectations

| Checkpoint | Evidence |
| --- | --- |
| CP1 Foundation | Frontend/backend build, run, and communicate through /api/health |
| CP2 Dashboard | Existing project registration, details, saved tasks/history and settings work |
| CP3 Isolation | Separate project records and settings; canonical file boundaries; rejected overlapping/platform folders |
| CP4 AI planning | Real provider request yields a stored project-specific plan with reviewed context |
| CP5 Controlled execution | Sandboxed change in a test repository; actual checks, diffs, logs and bounded recovery |
| CP6 Git | Reviewed changes committed/pushed without unrelated files |
| CP7 Deployment | Test environment returns a real deployment result and URL |
| CP8 Autonomy | Limits, cancellation and restart recovery hold across the entire loop |

An unconfigured integration must remain visibly unavailable. Never substitute a fabricated successful task, AI output, Git push, or deployment result.
