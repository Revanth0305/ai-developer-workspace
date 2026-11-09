import { useEffect, useRef, useState } from "react";
import GitPanel from "./GitPanel";

type Runner = { enabled: boolean; available: boolean; profile: string; imageId: string; message: string };
type Preview = { hash: string; prompt: string; files: string[]; newFiles: string[]; repairOf: string | null; baseCommit: string; profile: string };
type Attempt = {
  id: string; status: string; repairOf: string | null; baseCommit: string; profile: string; imageId: string;
  reviewHash: string; candidateFingerprint: string; checkRuns: number; exitCode: number | null; testCount: number | null;
  cleanupPending: boolean; contextPreview: string; selectedPaths: string; diff: string; logs: string; summary: string; error: string; createdAt: string;
};
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, { ...init, headers: { "Content-Type": "application/json" }, signal: init?.signal ?? AbortSignal.timeout(60000) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || `Request failed (${response.status}).`);
  return data as T;
}
const date = (value: string) => new Date(value).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
const running = (status: string) => status === "GENERATING" || status === "CHECKING";
const labels: Record<string, string> = { GENERATING: "Preparing proposal", REVIEW: "Review changes", CHECKING: "Running checks", PASSED: "Checks passed", FAILED: "Failed", CANCELLED: "Cancelled", INTERRUPTED: "Interrupted" };

export default function ExecutionPanel({ taskId, projectId, taskStatus, onChanged }: {
  taskId: string; projectId: string; taskStatus: string; onChanged: () => void;
}) {
  const [runner, setRunner] = useState<Runner | null>(null);
  const [files, setFiles] = useState<string[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [newFiles, setNewFiles] = useState("");
  const [filter, setFilter] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const alive = useRef(true);
  const callback = useRef(onChanged);
  const previous = useRef<Record<string, string>>({});
  callback.current = onChanged;
  const attempt = attempts.find((value) => value.id === selectedId);
  const active = attempts.some((value) => running(value.status));
  const addedPaths = newFiles.split(/\r?\n/).map((path) => path.trim()).filter(Boolean);
  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      request<Runner>("/execution/status", { signal: controller.signal }),
      request<string[]>(`/projects/${projectId}/files`, { signal: controller.signal }),
    ]).then(([runtime, paths]) => { if (!controller.signal.aborted) { setRunner(runtime); setFiles(paths); } })
      .catch((e) => { if (!controller.signal.aborted) setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [projectId]);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const history = await request<Attempt[]>(`/tasks/${taskId}/executions`, { signal: controller.signal });
        if (controller.signal.aborted) return;
        for (const item of history) {
          if (previous.current[item.id] && previous.current[item.id] !== item.status) callback.current();
          previous.current[item.id] = item.status;
          if (item.status === "CHECKING") {
            try { item.logs = (await request<{ logs: string }>(`/executions/${item.id}/logs`, { signal: controller.signal })).logs; } catch { /* The persisted result is loaded on the next poll. */ }
          }
        }
        if (controller.signal.aborted) return;
        setAttempts(history);
        setSelectedId((current) => history.some((item) => item.id === current) ? current : history[0]?.id || "");
      } catch (e) { if (!controller.signal.aborted) setError((e as Error).message); }
      if (!controller.signal.aborted) timer = setTimeout(() => void poll(), 2000);
    }
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [taskId]);
  async function refreshRunner() {
    setBusy(true); setError("");
    try { const value = await request<Runner>("/execution/status/refresh", { method: "POST" }); if (alive.current) { setRunner(value); callback.current(); } }
    catch (e) { if (alive.current) setError((e as Error).message); }
    finally { if (alive.current) setBusy(false); }
  }
  async function review(repair?: Attempt) {
    setBusy(true); setError(""); setPreview(null);
    try {
      const selection = repair ? JSON.parse(repair.selectedPaths) as { files: string[]; newFiles: string[] } : { files: selected, newFiles: addedPaths };
      if (repair) { setSelected(selection.files); setNewFiles(selection.newFiles.join("\n")); }
      const value = await request<Preview>(`/tasks/${taskId}/execution-context`, { method: "POST", body: JSON.stringify({ ...selection, repairOf: repair?.id || null }) });
      if (alive.current) setPreview(value);
    } catch (e) { if (alive.current) setError((e as Error).message); }
    finally { if (alive.current) setBusy(false); }
  }
  async function propose() {
    if (!preview) return;
    setBusy(true); setError("");
    try {
      const value = await request<Attempt>(`/tasks/${taskId}/executions`, { method: "POST", body: JSON.stringify({ files: preview.files, newFiles: preview.newFiles, repairOf: preview.repairOf, contextHash: preview.hash }) });
      if (alive.current) { setAttempts((current) => [value, ...current.filter((item) => item.id !== value.id)]); setSelectedId(value.id); setPreview(null); callback.current(); }
    } catch (e) { if (alive.current) { setError((e as Error).message); setPreview(null); } }
    finally { if (alive.current) setBusy(false); }
  }
  async function action(kind: "checks" | "cancel" | "cleanup") {
    if (!attempt) return;
    setBusy(true); setError("");
    try {
      const value = await request<Attempt>(`/executions/${attempt.id}/${kind}`, { method: "POST", ...(kind === "checks" ? { body: JSON.stringify({ reviewHash: attempt.reviewHash, imageId: runner?.imageId }) } : {}) });
      if (alive.current) { setAttempts((current) => current.map((item) => item.id === value.id ? value : item)); callback.current(); }
    } catch (e) { if (alive.current) setError((e as Error).message); }
    finally { if (alive.current) setBusy(false); }
  }
  return <section className="execution-panel" aria-label="Controlled execution">
    <div className="planning-heading"><h3>Controlled execution</h3><span className="badge neutral">CP5 · Node.js 24</span></div>
    <p className="source-note">Prepare changes in an isolated candidate, review the diff, then run containerized checks. The original project stays unchanged. A passing patch can be downloaded for your review.</p>
    <div className="runner-state" role="status">
      <strong>{runner?.available ? "Container runner connected" : "Container runner not ready"}</strong>
      <p>{loading ? "Checking the local runner…" : runner?.message || "Runner status could not be loaded."}</p>
      {runner?.available && <code>node --test --test-reporter=tap · {runner.imageId.slice(0, 24)}…</code>}
      <button type="button" className="button small" disabled={busy} onClick={() => void refreshRunner()}>Check runner</button>
    </div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {attempts.length > 0 && <>
      <label className="field-label" htmlFor="candidate-history">Candidate history ({attempts.length}/3)</label>
      <select id="candidate-history" value={selectedId} onChange={(e) => setSelectedId(e.target.value)}>
        {attempts.map((item, index) => <option key={item.id} value={item.id}>Candidate {attempts.length - index} · {labels[item.status] || item.status} · {date(item.createdAt)}</option>)}
      </select>
    </>}
    {attempt && <div className="candidate-result">
      <div className="planning-heading"><h3>{labels[attempt.status] || attempt.status}</h3><span className={`badge ${attempt.status === "PASSED" ? "green" : "neutral"}`}>{attempt.checkRuns}/3 check runs</span></div>
      {attempt.summary && <p className="source-note">{attempt.summary}</p>}
      <p className="source-note">Source commit {attempt.baseCommit.slice(0, 12)} · Original project unchanged</p>
      {running(attempt.status) && <p className="planning-progress" role="status"><span className="loader" />{attempt.status === "GENERATING" ? "Codex is preparing a file proposal. No project files are being edited." : "Checks are running with no network, read-only source, and bounded CPU, memory, process count and time."}</p>}
      {attempt.error && <p className="form-error">{attempt.error}</p>}
      {attempt.diff && <><h3>Review candidate diff</h3><pre className="candidate-diff" tabIndex={0}>{attempt.diff}</pre></>}
      {attempt.exitCode !== null && <p className="source-note">Actual container exit code: {attempt.exitCode}{attempt.testCount !== null ? ` · Tests reported: ${attempt.testCount}` : ""}</p>}
      {attempt.logs && <details open={attempt.status === "FAILED" || attempt.status === "CHECKING"}><summary>Actual check logs</summary><pre className="check-logs" tabIndex={0}>{attempt.logs}</pre></details>}
      <div className="execution-actions">
        {(attempt.status === "REVIEW" || attempt.status === "FAILED") && attempt.diff && attempt.checkRuns < 3 && !attempt.cleanupPending && <button type="button" className="button primary" disabled={busy || active || !runner?.available} onClick={() => void action("checks")}>{attempt.checkRuns ? "Retry reviewed checks" : "Run reviewed checks"}</button>}
        {attempt.status === "FAILED" && attempt.exitCode !== null && attempt.exitCode !== 0 && attempts.length < 3 && !attempt.cleanupPending && <button type="button" className="button secondary" disabled={busy || active} onClick={() => void review(attempt)}>Review repair context</button>}
        {(running(attempt.status) || attempt.status === "REVIEW") && <button type="button" className="button secondary" disabled={busy} onClick={() => void action("cancel")}>Cancel candidate operation</button>}
        {attempt.cleanupPending && <button type="button" className="button secondary" disabled={busy || active} onClick={() => void action("cleanup")}>Retry runner cleanup</button>}
        {attempt.status === "PASSED" && !attempt.cleanupPending && <a className="button secondary" href={`/api/executions/${attempt.id}/patch`} download>Download checked patch</a>}
      </div>
      <details><summary>Context sent for this candidate</summary><pre className="context-preview" tabIndex={0}>{attempt.contextPreview}</pre></details>
      {attempt.status === "PASSED" && !attempt.cleanupPending && <GitPanel key={attempt.id} id={attempt.id} />}
    </div>}
    {taskStatus !== "PLANNED" && <p className="source-note">Complete and review a plan before requesting file changes.</p>}
    {runner?.enabled && taskStatus === "PLANNED" && attempts.length < 3 && <details className="planning-request" open={!attempts.length}>
      <summary>Prepare a new file proposal</summary>
      <p className="source-note">Requires a clean Git repository with a commit. This first profile supports dependency-free Node.js tests; it does not install packages or run arbitrary host commands.</p>
      <label className="field-label" htmlFor="execution-filter">Authorize existing files ({selected.length + addedPaths.length}/8)</label>
      <input id="execution-filter" type="search" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Find a tracked source file…" />
      <div className="context-files" role="group" aria-label="Files Codex may change">
        {files.filter((path) => path.toLowerCase().includes(filter.toLowerCase())).map((path) => <label key={path}>
          <input type="checkbox" checked={selected.includes(path)} disabled={busy || active || (!selected.includes(path) && selected.length + addedPaths.length >= 8)} onChange={(e) => { setSelected((value) => e.target.checked ? [...value, path] : value.filter((item) => item !== path)); setPreview(null); }} /><span>{path}</span>
        </label>)}
      </div>
      <label className="field-label" htmlFor="execution-new-files">Authorize new file paths (one per line)</label>
      <textarea id="execution-new-files" rows={2} maxLength={8000} value={newFiles} disabled={busy || active} onChange={(e) => { setNewFiles(e.target.value); setPreview(null); }} placeholder="test/greeting.test.js" />
      <button type="button" className="button secondary" disabled={busy || active || selected.length + addedPaths.length === 0 || selected.length + addedPaths.length > 8} onClick={() => void review()}>Review change context</button>
    </details>}
    {attempts.length >= 3 && <p className="source-note">This task has reached its three-candidate limit. Review the retained results before creating a new instruction.</p>}
    {preview && <div className="context-review">
      <h3>{preview.repairOf ? "Review repair request" : "Review exact change request"}</h3>
      <p className="source-note">This includes the approved plan, authorized paths and selected source. Repair requests also include the previous candidate diff and redacted failed-check logs. Review the entire request for confidential content; detection is limited.</p>
      <pre className="context-preview" tabIndex={0}>{preview.prompt}</pre>
      <button type="button" className="button primary" disabled={busy || active} onClick={() => void propose()}>Request reviewed file proposal from Codex</button>
    </div>}
  </section>;
}
