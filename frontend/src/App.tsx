import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import "./App.css";
import ExecutionPanel from "./ExecutionPanel";
import HolographicOverview, { HoloIcon } from "./HolographicOverview";
import { DeploymentSummary } from "./DeploymentPanel";

type Project = {
  id: string;
  name: string;
  directory: string;
  instructions: string;
  createdAt: string;
  updatedAt: string;
};
type Task = {
  id: string;
  projectId: string;
  instruction: string;
  status: "DRAFT" | "PLANNING" | "PLANNED" | "FAILED" | "CANCELLED";
  createdAt: string;
};
type AiConnection = { enabled: boolean; authenticated: boolean; authMode: string; message: string };
type ContextPreview = { hash: string; prompt: string; selectedFiles: string[]; warnings: string[] };
type PlanAttempt = { id: string; status: string; contextPreview: string; result: string; error: string; startedAt: string; finishedAt: string | null };
const taskLabels: Record<Task["status"], string> = {
  DRAFT: "Draft", PLANNING: "Planning", PLANNED: "Plan ready", FAILED: "Failed", CANCELLED: "Cancelled",
};
type Activity = { id: string; message: string; createdAt: string };
type GitStatus = {
  repository: boolean;
  branch: string;
  changes: string;
  commits: string;
  message: string;
};
type View = "overview" | "workspace" | "projects" | "history" | "settings";
type Snapshot = { projects: Project[]; tasks: Task[]; activity: Activity[] };

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    signal: init?.signal ?? AbortSignal.timeout(15000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(body.message || `Request failed (${response.status}).`);
  return body as T;
}
const date = (value: string) =>
  new Date(value).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
const viewFromHash = (): View => {
  const value = window.location.hash.slice(1);
  return value === "workspace" || value === "projects" || value === "history" || value === "settings"
    ? value
    : "overview";
};
function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    grid: (
      <>
        <rect x="3" y="3" width="7" height="7" rx="1.5" />
        <rect x="14" y="3" width="7" height="7" rx="1.5" />
        <rect x="3" y="14" width="7" height="7" rx="1.5" />
        <rect x="14" y="14" width="7" height="7" rx="1.5" />
      </>
    ),
    folder: <path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10H3Z" />,
    clock: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </>
    ),
    settings: (
      <>
        <path d="m9 3-1 3-3 1v4l-2 1 2 3v3l3 1 1 2h5l1-3 3-1v-3l3-2-2-3V6l-4-1-1-2Z" />
        <circle cx="12" cy="12" r="3" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
    branch: (
      <>
        <circle cx="6" cy="5" r="2" />
        <circle cx="18" cy="6" r="2" />
        <circle cx="6" cy="19" r="2" />
        <path d="M6 7v10m0-5c8 0 12-1 12-4" />
      </>
    ),
    code: <path d="m8 7-5 5 5 5m8-10 5 5-5 5m-3-12-2 14" />,
    check: <path d="m5 12 4 4L19 6" />,
    close: <path d="m6 6 12 12M6 18 18 6" />,
    refresh: (
      <>
        <path d="M20 7v5h-5M4 17v-5h5" />
        <path d="M6 7a7 7 0 0 1 12-1l2 3M4 15l2 3a7 7 0 0 0 12-1" />
      </>
    ),
    shield: (
      <>
        <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z" />
        <path d="m8 12 3 3 5-6" />
      </>
    ),
    file: (
      <>
        <path d="M14 3H5v18h14V8Z" />
        <path d="M14 3v5h5M8 13h8m-8 4h5" />
      </>
    ),
    spark: (
      <path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5Z" />
    ),
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.code}
    </svg>
  );
}

function App() {
  const [aiConnection, setAiConnection] = useState<AiConnection | null>(null);
  const [runnerReady, setRunnerReady] = useState(false);
  const [deploymentEnabled, setDeploymentEnabled] = useState(false);
  const [view, setView] = useState<View>(viewFromHash);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [selectedId, setSelectedId] = useState(
    () => localStorage.getItem("workspace.project") || "",
  );
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [newProject, setNewProject] = useState(false);
  const [instruction, setInstruction] = useState("");
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState("");
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [activeTask, setActiveTaskState] = useState<Task | null>(null);
  const [taskPane, setTaskPane] = useState<"plan" | "execution">("plan");
  function setActiveTask(task: Task | null, pane: "plan" | "execution" = "plan") {
    setTaskPane(pane);
    setActiveTaskState(task);
  }
  const [refreshKey, setRefreshKey] = useState(0);
  const requestId = useRef(0);
  useEffect(() => {
    const controller = new AbortController();
    api<{ available: boolean }>("/execution/status", { signal: controller.signal })
      .then((value) => { if (!controller.signal.aborted) setRunnerReady(value.available); })
      .catch(() => { if (!controller.signal.aborted) setRunnerReady(false); });
    api<AiConnection>("/ai/status", { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setAiConnection(value); })
      .catch(() => { if (!controller.signal.aborted) setAiConnection(null); });
    api<{ enabled: boolean }>("/deployment/status", { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setDeploymentEnabled(value.enabled); })
      .catch(() => { if (!controller.signal.aborted) setDeploymentEnabled(false); });
    return () => controller.abort();
  }, [refreshKey]);
  const selected = snapshot?.projects.find(
    (project) => project.id === selectedId,
  );
  const projectTasks =
    snapshot?.tasks.filter((task) => task.projectId === selectedId) || [];
  const refresh = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    try {
      const [projects, tasks, activity] = await Promise.all([
        api<Project[]>("/projects"),
        api<Task[]>("/tasks"),
        api<Activity[]>("/activity"),
        api("/health"),
      ]);
      if (id !== requestId.current) return;
      setSnapshot({ projects, tasks, activity });
      setSelectedId((current) =>
        projects.some((project) => project.id === current)
          ? current
          : projects[0]?.id || "",
      );
      setError("");
      setRefreshKey((key) => key + 1);
    } catch (e) {
      if (id === requestId.current) setError((e as Error).message);
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    void refresh();
    return () => {
      requestId.current++;
    };
  }, [refresh]);
  useEffect(() => {
    localStorage.setItem("workspace.project", selectedId);
    setInstruction("");
  }, [selectedId]);
  useEffect(() => {
    const onHash = () => { setView(viewFromHash()); window.scrollTo(0, 0); };
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);
  useEffect(() => {
    if (notice) {
      const timeout = window.setTimeout(() => setNotice(""), 5000);
      return () => window.clearTimeout(timeout);
    }
  }, [notice]);
  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k" && !document.querySelector("dialog[open]")) {
        event.preventDefault();
        document.getElementById("workspace-navigation")?.hidePopover();
        window.location.hash = "overview";
        window.setTimeout(() => document.getElementById("jarvis-command")?.focus(), 0);
      }
    };
    window.addEventListener("keydown", shortcut);
    return () => window.removeEventListener("keydown", shortcut);
  }, []);
  function openWorkflow() {
    const task = projectTasks.find(task => task.status === "PLANNED") || projectTasks.find(task => task.status !== "CANCELLED");
    if (task) setActiveTask(task, task.status === "PLANNED" ? "execution" : "plan");
    else {
      setNotice("Save an instruction and review a plan to prepare code changes and checks.");
      document.getElementById("jarvis-command")?.focus();
    }
  }
  async function saveTask(event: FormEvent) {
    event.preventDefault();
    if (!selected || saving) return;
    setSaving(true);
    try {
      const saved = await api<Task>("/tasks", {
        method: "POST",
        body: JSON.stringify({ projectId: selected.id, instruction }),
      });
      setInstruction("");
      setNotice(
        "Draft saved. Review its context before sending a planning request.",
      );
      await refresh();
      setActiveTask(saved);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }
  async function cancelTask(task: Task) {
    setSaving(true);
    try {
      setActiveTask(
        await api<Task>(`/tasks/${task.id}/cancel`, { method: "POST" }),
      );
      setNotice("Draft cancelled.");
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }
  const nav = [
    { id: "overview", label: "Overview", icon: "grid" },
    { id: "workspace", label: "Workspace details", icon: "code" },
    { id: "projects", label: "Projects", icon: "folder" },
    { id: "history", label: "Task history", icon: "clock" },
    { id: "settings", label: "Settings", icon: "settings" },
  ] as const;
  const titles = {
    overview: "Good to see you.",
    workspace: "Workspace details",
    projects: "Your projects",
    history: "Task history",
    settings: "Workspace settings",
  };
  const descriptions = {
    overview: "Your projects, your ideas, your next move. All in one workspace.",
    workspace: "Your project, source, connections, progress and activity in one place.",
    projects:
      "Connect existing folders and keep every project in its own space.",
    history: "Every instruction has a place. Follow its status from here.",
    settings:
      "Project instructions and the current capabilities of your workspace.",
  };
  const filteredProjects =
    snapshot?.projects.filter((project) =>
      `${project.name} ${project.directory}`
        .toLowerCase()
        .includes(search.toLowerCase()),
    ) || [];

  return (
    <div className="app-shell holographic-shell">
      <div className="navigation-edge" aria-hidden="true" onPointerEnter={event => { if (event.pointerType === "mouse") document.getElementById("workspace-navigation")?.showPopover(); }} />
      <aside className="sidebar" id="workspace-navigation" popover="auto" aria-label="Workspace navigation"
        onToggle={event => setNavigationOpen((event.nativeEvent as ToggleEvent).newState === "open")}
        onPointerLeave={event => { if (event.pointerType === "mouse") event.currentTarget.hidePopover(); }}>
        <button type="button" className="navigation-close icon-button" popoverTarget="workspace-navigation" popoverTargetAction="hide" aria-label="Close navigation"><Icon name="close" size={19} /></button>
        <a className="brand" href="#overview" aria-label="Jarvis workspace overview">
          <span className="brand-mark">
            <Icon name="code" size={25} />
          </span>
          <span>
            JARVIS
            <span className="brand-caption">AI DEVELOPER WORKSPACE</span>
          </span>
        </a>
        <div className="workspace-label">
          <span className="workspace-avatar">L</span>
          <div>
            Local workspace<small>Personal environment</small>
          </div>
          <span className="tiny-dot" />
        </div>
        <p className="nav-label">WORKSPACE</p>
        <nav aria-label="Main navigation" onClick={() => document.getElementById("workspace-navigation")?.hidePopover()}>
          {nav.map((item) => (
            <a
              key={item.id}
              href={`#${item.id}`}
              className={view === item.id ? "nav-item active" : "nav-item"}
              aria-label={item.label}
              title={item.label}
              aria-current={view === item.id ? "page" : undefined}
            >
              <Icon name={item.icon} />
              {item.label}
              {item.id === "projects" && (
                <span className="nav-count">
                  {snapshot?.projects.length || 0}
                </span>
              )}
            </a>
          ))}
        </nav>
        <div className="sidebar-projects">
          <div className="nav-label project-label">
            PROJECTS
            <button
              className="icon-button"
              onClick={() => setNewProject(true)}
              aria-label="Register a project"
            >
              <Icon name="plus" size={16} />
            </button>
          </div>
          {snapshot?.projects.slice(0, 6).map((project) => (
            <button
              key={project.id}
              className={`sidebar-project ${project.id === selectedId ? "selected" : ""}`}
              onClick={() => {
                setSelectedId(project.id);
                window.location.hash = "overview";
              }}
            >
              <span className="project-dot" />
              {project.name}
            </button>
          ))}
          {!snapshot?.projects.length && (
            <p className="sidebar-empty">Your projects will appear here.</p>
          )}
        </div>
        <div className="sidebar-bottom">
          <div className="local-card">
            <Icon name="shield" size={19} />
            <div>
              Local by design<small>Files stay on your machine</small>
            </div>
          </div>
          <span className="version">
            HOLOGRAPHIC BUILD <span>v0.5</span>
          </span>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <button type="button" className="navigation-trigger" aria-label="Open navigation" aria-expanded={navigationOpen} aria-controls="workspace-navigation" popoverTarget="workspace-navigation"
            onPointerEnter={event => { if (event.pointerType === "mouse" && window.matchMedia("(min-width: 601px) and (hover: hover)").matches) document.getElementById("workspace-navigation")?.showPopover(); }}>
            <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true"><path d="M4 6h16M4 12h12M4 18h16" /></svg>
          </button>
          <a className="header-brand" href="#overview" aria-label="Jarvis home"><span className="brand-mark"><HoloIcon name="spark" size={22} /></span><span>JARVIS<small>AI Development Assistant</small></span></a>
          <div className="topbar-actions">
            <span
              className={`connection ${error || !snapshot ? "offline" : ""}`}
            >
              <span />
              {error
                ? "Connection needs attention"
                : snapshot
                  ? "Backend connected"
                  : "Connecting…"}
            </span>
            <button
              className="icon-button refresh"
              disabled={loading}
              onClick={() => void refresh()}
              aria-label="Refresh workspace"
            >
              <Icon name="refresh" size={18} />
            </button>
            <span className="user-avatar" title="Revanth · local workspace">
              R
            </span>
          </div>
        </header>
        <main className={view === "overview" ? "overview-main" : view === "workspace" ? "workspace-main" : undefined}>
          {view !== "overview" && <div className="page-heading">
            <div>
              <p className="eyebrow">YOUR COMMAND CENTER</p>
              <h1>{titles[view]}</h1>
              <p className="page-description">{descriptions[view]}</p>
            </div>
            <button
              className="button primary"
              onClick={() => setNewProject(true)}
            >
              <Icon name="plus" size={18} />
              Add project
            </button>
          </div>}
          {error && (
            <div className="alert error" role="alert">
              <div>
                <strong>Something needs attention</strong>
                <p>{error} Check that the backend is running, then refresh.</p>
              </div>
              <button className="button small" onClick={() => void refresh()}>
                Retry
              </button>
            </div>
          )}
          {notice && (
            <div className="alert success" role="status">
              <Icon name="check" />
              {notice}
            </div>
          )}
          {!snapshot && loading ? (
            <div className="empty panel">
              <span className="loader" />
              <h2>Connecting to your workspace</h2>
              <p>Loading projects and saved activity from the local backend.</p>
            </div>
          ) : (
            <>
              {view === "overview" && (
                <>
                  <HolographicOverview
                    projects={snapshot?.projects || []} tasks={snapshot?.tasks || []}
                    selectedId={selectedId} instruction={instruction} saving={saving}
                    connected={!!aiConnection?.authenticated} runnerReady={runnerReady}
                    onProject={setSelectedId} onInstruction={setInstruction}
                    onSubmit={event => void saveTask(event)} onAdd={() => setNewProject(true)}
                    onContinue={() => { const task = projectTasks.find(task => task.status !== "CANCELLED") || projectTasks[0]; if (task) setActiveTask(task); }}
                    onTask={id => { const task = snapshot?.tasks.find(task => task.id === id); if (task) setActiveTask(task); }}
                    onWorkflow={() => openWorkflow()}
                  />
                </>
              )}
              {view === "workspace" && (
                <>
                  <div className="work-grid">
                    <div className="work-main">
                      <section className="panel project-panel">
                        <div className="panel-heading">
                          <div>
                            <h2>Active project</h2>
                            <p>Choose the context for your next instruction.</p>
                          </div>
                          <Icon name="folder" />
                        </div>
                        {selected ? (
                          <>
                            <label
                              className="field-label"
                              htmlFor="active-project"
                            >
                              Project
                            </label>
                            <select
                              id="active-project"
                              value={selectedId}
                              onChange={(event) =>
                                setSelectedId(event.target.value)
                              }
                            >
                              {snapshot?.projects.map((project) => (
                                <option key={project.id} value={project.id}>
                                  {project.name}
                                </option>
                              ))}
                            </select>
                            <div className="project-path">
                              <Icon name="folder" size={16} />
                              <code>{selected.directory}</code>
                            </div>
                            <RepositoryPanel
                              key={selected.id}
                              project={selected}
                              refreshKey={refreshKey}
                            />
                          </>
                        ) : (
                          <Empty
                            icon="folder"
                            title="Connect your first project"
                            description="Register an existing folder outside this platform repository to get started."
                          >
                            <button
                              className="button secondary"
                              onClick={() => setNewProject(true)}
                            >
                              <Icon name="plus" size={17} />
                              Connect a folder
                            </button>
                          </Empty>
                        )}
                      </section>
                      {selected && (
                        <SourceBrowser
                          key={selected.id}
                          project={selected}
                          refreshKey={refreshKey}
                        />
                      )}
                    </div>
                    <div className="work-aside">
                      <CodexStatus onChange={setAiConnection} />
                      {selected && <DeploymentSummary key={selected.id} projectId={selected.id} onOpen={id => { const task = snapshot?.tasks.find(task => task.id === id); if (task) setActiveTask(task, "execution"); }} />}
                      <section className="panel">
                        <div className="panel-heading">
                          <h2>Development journey</h2>
                          <span className="badge neutral">7 / 8</span>
                        </div>
                        <ol className="journey">
                          {[
                            "Foundation",
                            "Dashboard",
                            "Project management",
                            "AI integration",
                            "Code automation",
                            "Git automation",
                            "Deployment",
                            "Autonomous workflow",
                          ].map((label, index) => (
                            <li
                              key={label}
                              className={
                                index < 7 ? "done" : index === 7 ? "next" : ""
                              }
                            >
                              <span className="journey-step">
                                {index < 7 ? (
                                  <Icon name="check" size={13} />
                                ) : (
                                  index + 1
                                )}
                              </span>
                              <span>
                                {label}
                                {index === 7 && (
                                  <small>Awaiting your milestone review</small>
                                )}
                              </span>
                              {index === 7 && (
                                <span className="next-label">NEXT</span>
                              )}
                            </li>
                          ))}
                        </ol>
                      </section>
                      <section className="panel">
                        <div className="panel-heading">
                          <h2>Recent activity</h2>
                          <Icon name="clock" size={18} />
                        </div>
                        <ActivityList
                          items={snapshot?.activity.slice(0, 6) || []}
                        />
                      </section>
                    </div>
                  </div>
                  <section className="panel task-panel">
                    <div className="panel-heading">
                      <div>
                        <h2>Project instructions</h2>
                        <p>
                          {selected
                            ? `Saved tasks for ${selected.name}`
                            : "Select a project to see its instructions."}
                        </p>
                      </div>
                      <a href="#history" className="text-link">
                        View all <Icon name="arrow" size={16} />
                      </a>
                    </div>
                    <TaskList
                      tasks={projectTasks.slice(0, 5)}
                      projects={snapshot?.projects || []}
                      onSelect={setActiveTask}
                    />
                  </section>
                </>
              )}
              {view === "projects" && (
                <>
                  <div className="section-toolbar">
                    <span>
                      {snapshot?.projects.length || 0} connected projects
                    </span>
                    <label className="search">
                      <Icon name="folder" size={17} />
                      <input
                        aria-label="Search projects"
                        placeholder="Search projects…"
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                      />
                    </label>
                  </div>
                  <div className="project-grid">
                    {filteredProjects.map((project) => (
                      <button
                        className="project-card panel"
                        key={project.id}
                        onClick={() => {
                          setSelectedId(project.id);
                          window.location.hash = "overview";
                        }}
                      >
                        <div className="project-card-top">
                          <span className="icon-tile">
                            <Icon name="folder" size={23} />
                          </span>
                          <span className="badge neutral">Local project</span>
                        </div>
                        <h2>{project.name}</h2>
                        <p className="card-path">{project.directory}</p>
                        <div className="project-card-bottom">
                          <span>
                            {snapshot?.tasks.filter(
                              (task) => task.projectId === project.id,
                            ).length || 0}{" "}
                            instructions
                          </span>
                          <Icon name="arrow" size={18} />
                        </div>
                      </button>
                    ))}
                    <button
                      className="add-project-card"
                      onClick={() => setNewProject(true)}
                    >
                      <span className="add-circle">
                        <Icon name="plus" size={24} />
                      </span>
                      <strong>Connect a project</strong>
                      <span>Use an existing local folder</span>
                    </button>
                  </div>
                  {search && !filteredProjects.length && (
                    <p className="muted">No projects match your search.</p>
                  )}
                  <div className="footnote">
                    <Icon name="shield" size={18} />
                    <p>
                      Project folders remain separate from this application.
                      Registering a folder does not modify its files or
                      initialize Git.
                    </p>
                  </div>
                </>
              )}
              {view === "history" && (
                <section className="panel">
                  <div className="panel-heading">
                    <div>
                      <h2>All instructions</h2>
                      <p>
                        Drafts and cancellations are retained across restarts.
                      </p>
                    </div>
                    <span className="badge neutral">
                      {snapshot?.tasks.length || 0} tasks
                    </span>
                  </div>
                  <TaskList
                    tasks={snapshot?.tasks || []}
                    projects={snapshot?.projects || []}
                    onSelect={setActiveTask}
                  />
                </section>
              )}
              {view === "settings" && (
                <div className="settings-grid">
                  <section className="panel">
                    <div className="panel-heading">
                      <div>
                        <h2>Project instructions</h2>
                        <p>
                          Keep conventions and context specific to each project.
                        </p>
                      </div>
                      <Icon name="settings" />
                    </div>
                    {selected ? (
                      <>
                        <label
                          className="field-label"
                          htmlFor="settings-project"
                        >
                          Project
                        </label>
                        <select
                          id="settings-project"
                          value={selectedId}
                          onChange={(event) =>
                            setSelectedId(event.target.value)
                          }
                        >
                          {snapshot?.projects.map((project) => (
                            <option key={project.id} value={project.id}>
                              {project.name}
                            </option>
                          ))}
                        </select>
                        <ProjectSettings
                          key={`${selected.id}-${selected.updatedAt}`}
                          project={selected}
                          onSaved={async () => {
                            setNotice("Project settings saved.");
                            await refresh();
                          }}
                          onError={setError}
                        />
                      </>
                    ) : (
                      <Empty
                        icon="folder"
                        title="No project selected"
                        description="Add a project to configure its instructions."
                      />
                    )}
                  </section>
                  <section className="panel">
                    <div className="panel-heading">
                      <h2>Platform capabilities</h2>
                      <Icon name="shield" />
                    </div>
                    {[
                      ["Local persistence", "Enabled"],
                      ["Git & source inspection", "Read only"],
                      ["AI provider", aiConnection?.authenticated ? "Codex · connected" : "Codex · not connected"],
                      ["Code execution", runnerReady ? "Isolated Node.js 24 tests" : "Runner not connected"],
                      ["Private commits & GitHub", "Reviewed · opt-in"],
                      ["Deployment", deploymentEnabled ? "Local Docker static sites" : "Disabled for this session"],
                    ].map(([label, value], index) => (
                      <div key={label} className="capability">
                        <span>{label}</span>
                        <span
                          className={`badge ${index < 2 ? "green" : "neutral"}`}
                        >
                          {value}
                        </span>
                      </div>
                    ))}
                    <p className="settings-note">
                      The service listens on your computer's loopback address.
                      Codex uses your local sign-in. Review each request before sending
                      it. Containerized checks require a connected runner. Git publishing requires a separate reviewed commit and confirmed destination. Local deployment requires a checked private commit, reviewed public assets and a separate deployment confirmation.
                    </p>
                  </section>
                </div>
              )}
            </>
          )}
          {view !== "overview" && <footer className="page-footer">
            <span>
              <span className="tiny-dot" />
              Your workspace. Your machine.
            </span>
            <span>Build → Test → Verify → Commit → Continue</span>
          </footer>}
        </main>
      </div>
      {newProject && (
        <ProjectModal
          onClose={() => setNewProject(false)}
          onCreated={async (project) => {
            setSelectedId(project.id);
            setNewProject(false);
            window.location.hash = "overview";
            setNotice("Project connected. Its files have not been modified.");
            await refresh();
          }}
        />
      )}
      {activeTask && (
        <Modal title="Instruction workspace" onClose={() => setActiveTask(null)}>
          <div className="task-detail-meta">
            <span
              className={`badge ${activeTask.status === "DRAFT" ? "purple" : "neutral"}`}
            >
              {activeTask.status}
            </span>
            <span>{date(activeTask.createdAt)}</span>
          </div>
          <h3>
            {snapshot?.projects.find(
              (project) => project.id === activeTask.projectId,
            )?.name || "Project"}
          </h3>
          <pre className="task-instruction">{activeTask.instruction}</pre>
          <div className="instruction-views" role="group" aria-label="Instruction views">
            <button type="button" className="button secondary" aria-pressed={taskPane === "plan"} onClick={() => setTaskPane("plan")}>Plan & context</button>
            <button type="button" className="button secondary" aria-pressed={taskPane === "execution"} onClick={() => setTaskPane("execution")}>Code & checks</button>
          </div>
          {taskPane === "plan" ? <PlanningPanel
            key={activeTask.id}
            task={activeTask}
            project={snapshot?.projects.find((project) => project.id === activeTask.projectId)}
            onUpdate={(task) => { setActiveTask(task); void refresh(); }}
          /> : <ExecutionPanel taskId={activeTask.id} projectId={activeTask.projectId} taskStatus={activeTask.status} onChanged={() => void refresh()} />}
          {taskPane === "plan" && (activeTask.status === "DRAFT" || activeTask.status === "PLANNING") && (
            <button
              className="button secondary"
              disabled={saving}
              onClick={() => void cancelTask(activeTask)}
            >
              {saving ? "Cancelling…" : activeTask.status === "PLANNING" ? "Cancel planning" : "Cancel draft"}
            </button>
          )}
        </Modal>
      )}
    </div>
  );
}
function CodexStatus({ onChange }: { onChange: (status: AiConnection) => void }) {
  const [connection, setConnection] = useState<AiConnection | null>(null);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState("");
  const callback = useRef(onChange);
  callback.current = onChange;
  useEffect(() => {
    const controller = new AbortController();
    setChecking(true);
    api<AiConnection>("/ai/status", { signal: controller.signal })
      .then((value) => { if (!controller.signal.aborted) { setConnection(value); callback.current(value); } })
      .catch(() => { if (!controller.signal.aborted) setError("The Codex connection could not be checked."); })
      .finally(() => { if (!controller.signal.aborted) setChecking(false); });
    return () => controller.abort();
  }, []);
  async function check() {
    setChecking(true); setError("");
    try {
      const value = await api<AiConnection>("/ai/status/refresh", { method: "POST", signal: AbortSignal.timeout(60000) });
      setConnection(value); callback.current(value);
    } catch { setError("The Codex connection could not be checked. Try again."); }
    finally { setChecking(false); }
  }
  return <section className="panel assistant-core">
    <div className="panel-heading"><h2>Codex connection</h2><Icon name="spark" size={18} /></div>
    <div className="core-visual" aria-hidden="true"><span className="core-orb" /></div>
    <p className="core-status" role="status">{checking ? "CHECKING CONNECTION" : connection?.authenticated ? "READ-ONLY PLANNING READY" : "PLANNING NOT CONNECTED"}</p>
    <p className="core-description">{error || connection?.message || "Checking your local Codex sign-in."}</p>
    <button type="button" className="button secondary connection-check" disabled={checking} onClick={() => void check()}>
      <Icon name="refresh" size={15} />{checking ? "Checking…" : "Check connection"}
    </button>
  </section>;
}

function PlanningPanel({ task, project, onUpdate }: {
  task: Task; project?: Project; onUpdate: (task: Task) => void;
}) {
  const [files, setFiles] = useState<string[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [preview, setPreview] = useState<ContextPreview | null>(null);
  const [attempts, setAttempts] = useState<PlanAttempt[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [connection, setConnection] = useState<AiConnection | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const callback = useRef(onUpdate);
  callback.current = onUpdate;
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    Promise.all([
      api<string[]>(`/projects/${task.projectId}/files`, { signal: controller.signal }),
      api<AiConnection>("/ai/status", { signal: controller.signal }),
    ]).then(([paths, status]) => { if (!controller.signal.aborted) { setFiles(paths); setConnection(status); } })
      .catch((e) => { if (!controller.signal.aborted) setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [task.projectId]);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const [current, history] = await Promise.all([
          api<Task>(`/tasks/${task.id}`, { signal: controller.signal }),
          api<PlanAttempt[]>(`/tasks/${task.id}/attempts`, { signal: controller.signal }),
        ]);
        if (controller.signal.aborted) return;
        setAttempts(history);
        setHistoryLoading(false);
        if (current.status !== task.status) callback.current(current);
        if (current.status === "PLANNING") timer = setTimeout(() => void poll(), 1500);
      } catch (e) {
        if (!controller.signal.aborted) {
          setError((e as Error).message);
          if (task.status === "PLANNING") timer = setTimeout(() => void poll(), 3000);
        }
      }
    }
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [task.id, task.status]);
  useEffect(() => { setPreview(null); }, [task.status]);

  async function review() {
    setBusy(true); setError(""); setPreview(null);
    try {
      const value = await api<ContextPreview>(`/tasks/${task.id}/context`, { method: "POST", body: JSON.stringify({ files: selected }) });
      if (mounted.current) setPreview(value);
    } catch (e) { if (mounted.current) setError((e as Error).message); }
    finally { if (mounted.current) setBusy(false); }
  }
  async function send() {
    if (!preview) return;
    setBusy(true); setError("");
    try {
      await api<PlanAttempt>(`/tasks/${task.id}/plan`, {
        method: "POST", body: JSON.stringify({ files: preview.selectedFiles, contextHash: preview.hash }), signal: AbortSignal.timeout(60000),
      });
      const current = await api<Task>(`/tasks/${task.id}`);
      if (mounted.current) { setPreview(null); callback.current(current); }
    } catch (e) { if (mounted.current) { setError((e as Error).message); setPreview(null); } }
    finally { if (mounted.current) setBusy(false); }
  }
  const canPlan = task.status === "DRAFT" || task.status === "FAILED" || task.status === "PLANNED";
  return <section className="planning-panel" aria-label="Read-only planning">
    <div className="planning-heading"><h3>Read-only planning</h3><span className="badge neutral">{taskLabels[task.status]}</span></div>
    <p className="source-note">{project?.name || "This project"} · Plans are proposals. No files, commands, commits or deployments are executed.</p>
    {task.status === "PLANNING" && <div className="planning-progress" role="status"><span className="loader" />Codex is preparing your plan. You can close this window and return from task history.</div>}
    {task.status === "CANCELLED" && <p className="source-note">This task was cancelled. Its previous attempts remain available below.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="plan-attempts">
      <h3>Planning history</h3>
      {!attempts.length && <p className="source-note">{historyLoading ? "Loading planning history…" : "No planning requests have been sent for this instruction."}</p>}
      {attempts.map((attempt, index) => <details key={attempt.id} open={index === 0}>
        <summary>{attempt.status === "SUCCEEDED" ? "Plan ready" : attempt.status.toLowerCase()} · {date(attempt.startedAt)}</summary>
        {attempt.result && <pre className="plan-output" tabIndex={0}>{attempt.result}</pre>}
        {attempt.error && <p className="form-error">{attempt.error}</p>}
        {attempt.status === "CANCELLED" && <p className="source-note">Cancelled. A late result will not replace this state.</p>}
        <details><summary>Context sent for this attempt</summary><pre className="context-preview" tabIndex={0}>{attempt.contextPreview}</pre></details>
      </details>)}
    </div>
    {canPlan && <details className="planning-request" open={task.status !== "PLANNED"}>
      <summary>{task.status === "PLANNED" ? "Review context for another plan" : "Prepare a planning request"}</summary>
      <p className="source-note">{loading ? "Loading source choices and connection…" : connection?.message || "Codex is not connected."}</p>
      <label className="field-label" htmlFor="context-filter">Choose source context ({selected.length}/8)</label>
      <input id="context-filter" type="search" placeholder="Find a source file…" value={filter} onChange={(e) => setFilter(e.target.value)} disabled={busy || loading} />
      <div className="context-files" role="group" aria-label="Source files to include">
        {files.filter((path) => path.toLowerCase().includes(filter.toLowerCase())).map((path) => <label key={path}>
          <input type="checkbox" checked={selected.includes(path)} disabled={busy || (!selected.includes(path) && selected.length >= 8)}
            onChange={(e) => { setSelected((current) => e.target.checked ? [...current, path] : current.filter((item) => item !== path)); setPreview(null); }} />
          <span>{path}</span>
        </label>)}
        {!loading && !files.length && <p className="source-note">No supported source files found. You can still plan from the instruction and project conventions.</p>}
      </div>
      <p className="source-note">Up to 8 files, 12,000 characters per file and 24,000 total. The project name, conventions, instruction and bounded source path listing are also included. No file contents are sent until you review and confirm.</p>
      <button type="button" className="button secondary" onClick={() => void review()} disabled={busy || loading}>{busy ? "Working…" : "Review context"}</button>
      {preview && <div className="context-review">
        <h3>Review the exact request</h3>
        {preview.warnings.map((warning) => <p className="source-note" key={warning}>{warning}</p>)}
        <p className="source-note">Credential detection is limited. Check the entire preview for confidential content before sending.</p>
        <pre className="context-preview" tabIndex={0}>{preview.prompt}</pre>
        <button type="button" className="button primary" onClick={() => void send()} disabled={busy || !connection?.authenticated}>
          {busy ? "Starting…" : "Send reviewed context to Codex"}<Icon name="arrow" size={16} />
        </button>
      </div>}
    </details>}
  </section>;
}

function Empty({
  icon,
  title,
  description,
  children,
}: {
  icon: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Icon name={icon} size={25} />
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
      {children}
    </div>
  );
}
function ActivityList({ items }: { items: Activity[] }) {
  if (!items.length)
    return (
      <div className="quiet-empty">
        <Icon name="clock" size={24} />
        <p>A fresh start.</p>
        <small>Project and task activity will appear here.</small>
      </div>
    );
  return (
    <div className="activity-list">
      {items.map((item) => (
        <div className="activity-item" key={item.id}>
          <span className="activity-dot" />
          <div>
            <p>{item.message}</p>
            <time>{date(item.createdAt)}</time>
          </div>
        </div>
      ))}
    </div>
  );
}
function TaskList({
  tasks,
  projects,
  onSelect,
}: {
  tasks: Task[];
  projects: Project[];
  onSelect: (task: Task) => void;
}) {
  if (!tasks.length)
    return (
      <div className="task-empty">
        <Icon name="file" size={23} />
        <span>
          No instructions yet. Save your first draft from the overview.
        </span>
      </div>
    );
  return (
    <div className="task-list">
      {tasks.map((task) => (
        <button
          className="task-row"
          key={task.id}
          onClick={() => onSelect(task)}
        >
          <span className="task-symbol">
            <Icon name="file" size={19} />
          </span>
          <span className="task-text">
            <strong>{task.instruction}</strong>
            <small>
              {projects.find((project) => project.id === task.projectId)
                ?.name || "Project"}{" "}
              <span>·</span> {date(task.createdAt)}
            </small>
          </span>
          <span
            className={`badge ${task.status === "DRAFT" ? "purple" : "neutral"}`}
          >
            {taskLabels[task.status]}
          </span>
          <Icon name="arrow" size={17} />
        </button>
      ))}
    </div>
  );
}
function RepositoryPanel({
  project,
  refreshKey,
}: {
  project: Project;
  refreshKey: number;
}) {
  const [status, setStatus] = useState<GitStatus | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setStatus(null);
    setError("");
    api<GitStatus>(`/projects/${project.id}/git`, { signal: controller.signal })
      .then(setStatus)
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      });
    return () => controller.abort();
  }, [project.id, refreshKey]);
  return (
    <div className="repo-status">
      <div>
        <Icon name="branch" size={17} />
        <strong>
          {error
            ? "Inspection unavailable"
            : status
              ? status.repository
                ? status.branch
                : "No Git repository"
              : "Inspecting repository…"}
        </strong>
        {status?.repository && (
          <span className={`badge ${status.changes ? "amber" : "green"}`}>
            {status.changes ? "Uncommitted changes" : "Working tree clean"}
          </span>
        )}
      </div>
      {error ? (
        <p className="muted">{error}</p>
      ) : status?.repository ? (
        <>
          <p className="muted">
            {status.commits.split("\n")[0] || "No commits yet"}
          </p>
          {status.changes && (
            <details>
              <summary>View working tree changes</summary>
              <pre>{status.changes}</pre>
            </details>
          )}
        </>
      ) : (
        status && (
          <p className="muted">
            {status.message} You can still save instructions.
          </p>
        )
      )}
    </div>
  );
}
function SourceBrowser({
  project,
  refreshKey,
}: {
  project: Project;
  refreshKey: number;
}) {
  const [files, setFiles] = useState<string[]>([]);
  const [selectedFile, setSelectedFile] = useState("");
  const [content, setContent] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    setBusy(true);
    setError("");
    api<string[]>(`/projects/${project.id}/files`, {
      signal: controller.signal,
    })
      .then(setFiles)
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    return () => controller.abort();
  }, [project.id, refreshKey]);
  useEffect(() => {
    if (!selectedFile) return;
    const controller = new AbortController();
    setContent("Loading source…");
    setError("");
    api<{ content: string }>(
      `/projects/${project.id}/file?path=${encodeURIComponent(selectedFile)}`,
      { signal: controller.signal },
    )
      .then((result) => setContent(result.content))
      .catch((e) => {
        if (!controller.signal.aborted) {
          setError(e.message);
          setContent("");
        }
      });
    return () => controller.abort();
  }, [project.id, selectedFile]);
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Source preview</h2>
          <p>Read-only context from the selected folder.</p>
        </div>
        <span className="badge neutral">
          {files.length}
          {files.length === 250 ? "+" : ""} files
        </span>
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {busy ? (
        <p className="muted">Reading project files…</p>
      ) : files.length ? (
        <>
          <label className="field-label" htmlFor="source-file">
            Source file
          </label>
          <select
            id="source-file"
            value={selectedFile}
            onChange={(event) => setSelectedFile(event.target.value)}
          >
            <option value="">Choose a file to preview</option>
            {files.map((file) => (
              <option key={file} value={file}>
                {file}
              </option>
            ))}
          </select>
          {selectedFile && (
            <pre className="source-preview">
              <code>{content}</code>
            </pre>
          )}
        </>
      ) : (
        <p className="muted">
          No supported source files found. Generated folders and hidden files
          are excluded.
        </p>
      )}
      <p className="source-note">
        Bounded to 250 files and 12 folder levels. Hidden files, common
        credential filenames, and generated folders are excluded. Nothing is
        sent to AI.
      </p>
    </section>
  );
}
function ProjectSettings({
  project,
  onSaved,
  onError,
}: {
  project: Project;
  onSaved: () => Promise<void>;
  onError: (value: string) => void;
}) {
  const [name, setName] = useState(project.name);
  const [instructions, setInstructions] = useState(project.instructions);
  const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    try {
      await api(`/projects/${project.id}`, {
        method: "PUT",
        body: JSON.stringify({ name, instructions }),
      });
      await onSaved();
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }
  return (
    <form className="settings-form" onSubmit={(event) => void submit(event)}>
      <label className="field-label" htmlFor="project-name">
        Project name
      </label>
      <input
        id="project-name"
        required
        maxLength={100}
        value={name}
        onChange={(event) => setName(event.target.value)}
      />
      <label className="field-label" htmlFor="project-instructions">
        Project conventions & instructions
      </label>
      <textarea
        id="project-instructions"
        rows={8}
        maxLength={8000}
        value={instructions}
        onChange={(event) => setInstructions(event.target.value)}
        placeholder="Describe the stack, coding conventions, checks, and boundaries for this project."
      />
      <p className="source-note">
        These settings are saved separately for this project. They are not sent
        to an AI provider yet.
      </p>
      <button className="button primary" disabled={saving || !name.trim()}>
        {saving ? "Saving…" : "Save settings"}
      </button>
    </form>
  );
}
function ProjectModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (project: Project) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [directory, setDirectory] = useState("");
  const [instructions, setInstructions] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      await onCreated(
        await api<Project>("/projects", {
          method: "POST",
          body: JSON.stringify({ name, directory, instructions }),
        }),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }
  return (
    <Modal title="Connect a project" onClose={saving ? undefined : onClose}>
      <p className="modal-description">
        Register an existing project folder. Its files stay where they are.
      </p>
      <form onSubmit={(event) => void submit(event)}>
        <label className="field-label" htmlFor="new-name">
          Project name
        </label>
        <input
          id="new-name"
          autoFocus
          required
          maxLength={100}
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Fleet Management"
        />
        <label className="field-label" htmlFor="new-directory">
          Absolute folder path
        </label>
        <input
          id="new-directory"
          required
          maxLength={2048}
          value={directory}
          onChange={(event) => setDirectory(event.target.value)}
          placeholder="C:\Development\fleet-management"
        />
        <p className="field-hint">
          Choose an existing folder outside this platform repository. Copy its
          path from File Explorer.
        </p>
        <label className="field-label" htmlFor="new-instructions">
          Project instructions <span className="muted">(optional)</span>
        </label>
        <textarea
          id="new-instructions"
          rows={3}
          maxLength={8000}
          value={instructions}
          onChange={(event) => setInstructions(event.target.value)}
          placeholder="Stack, coding conventions, and relevant checks…"
        />
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <div className="modal-actions">
          <button
            type="button"
            className="button secondary"
            disabled={saving}
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            className="button primary"
            disabled={saving || !name.trim() || !directory.trim()}
          >
            {saving ? "Connecting…" : "Connect project"}
            <Icon name="arrow" size={17} />
          </button>
        </div>
      </form>
    </Modal>
  );
}
function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose?: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement as HTMLElement | null;
    dialog?.showModal();
    const cancel = (event: Event) => {
      event.preventDefault();
      closeRef.current?.();
    };
    dialog?.addEventListener("cancel", cancel);
    return () => {
      dialog?.removeEventListener("cancel", cancel);
      dialog?.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog ref={ref} className="modal" aria-labelledby="modal-title">
      <div className="modal-heading">
        <h2 id="modal-title">{title}</h2>
        <button
          disabled={!onClose}
          className="icon-button"
          aria-label="Close dialog"
          onClick={onClose}
        >
          <Icon name="close" />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export default App;
