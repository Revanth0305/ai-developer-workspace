# Development environment check

The initial section records the source computer. See the destination verification at the end.

Checked: 2026-11-08

Workspace: `C:\Users\purch\OneDrive\Documents\ChatGPT\Automation Project`

| Requirement | Result |
| --- | --- |
| Node.js | v24.14.1 |
| npm | 11.11.0 |
| Git | 2.53.0.windows.2 |
| Java runtime | Eclipse Temurin OpenJDK 21.0.13+9 LTS; verified |
| Java compiler | javac 21.0.13; verified |
| JAVA_HOME | Machine setting: C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot\ |
| Maven | Not available on PATH |
| Gradle | Project Gradle Wrapper 9.8.2 verified; global installation unnecessary |

Eclipse Temurin JDK 21 was installed using Windows Package Manager. The installer configured machine JAVA_HOME and added the JDK bin directory to the machine PATH. Existing terminals may need to be reopened to pick up these settings.

Verification: java and javac report version 21.0.13. A temporary EnvironmentCheck.java program compiled and ran successfully, printing JAVA_SETUP_OK 21.0.13.

At the initial inspection, the Git repository was initialized on master with no commits or application source files. The frontend, backend, scripts and documentation have since been implemented; see PROGRESS_TRACKER.md for verification.

## Next actions

1. Completed: frontend and backend structure with project Gradle Wrapper.
2. Completed: build, integration/isolation tests, local servers and API communication.
3. Next: select an AI provider when ready; see PROGRESS_TRACKER.md.

Step 1 is complete. Node.js, npm, Git, the JDK and the project build tool have been verified.


## Destination verification — 2026-11-08

Git 2.56.0, Node.js 24.15.0, npm 11.13.0 and Eclipse Temurin JDK/javac 21.0.13 are verified. Machine JAVA_HOME points to the Temurin JDK 21 installation. Gradle Wrapper 9.8.2 builds the executable backend JAR. Codex App Server 0.167.0-alpha.3.2 reports local ChatGPT sign-in and passed a live read-only planning check.

The Codex execution environment supplies a long temporary path that exceeds Java's local socket path limit. Validation uses a process-only `jdk.net.unixdomain.tmpdir` property pointing to a shorter local temporary folder. This is an environment-specific workaround, not a machine security or execution-policy change. Windows Application Control still blocks oxlint's native binding; lint has not been verified. TypeScript checking, production build and backend tests are separate checks.

## CP5 runtime verification — 2026-11-09

Docker Desktop 4.98.0, Docker Engine/CLI 29.9.2 and WSL 2.7.14 are installed. Windows was restarted after enabling VirtualMachinePlatform; the Linux engine is working. The official Node 24 Alpine image is prepared. Both optional runtime compatibility tests passed in the 47-test destination build. The background frontend receives child-only CI=true to stay running when the launcher input closes. Local images, volumes, databases, sign-in state and dependencies are machine-specific and do not synchronize through Git.
