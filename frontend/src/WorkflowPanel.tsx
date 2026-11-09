import { useEffect, useState } from "react";
type Input = { files: string[]; newFiles: string[]; maxCandidates: number };
type Preview = { hash: string; prompt: string; policy: string; imageId: string; input: Input };
type Run = { id: string; executionId: string | null; status: string; candidates: number; maxCandidates: number; error: string; events: string; cleanupPending: boolean; deadline: string };
async function api<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api${path}`, { ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}), signal: signal ?? AbortSignal.timeout(60000) });
  const value = await response.json(); if (!response.ok) throw new Error(value.message || "Workflow request failed. Check its retained result."); return value as T;
}
const running = (s: string) => ["PROPOSING", "CHECKING", "STOPPING"].includes(s);
const labels: Record<string, string> = { PROPOSING: "Preparing candidate", CHECKING: "Running isolated checks", STOPPING: "Stopping owned work", REVIEW_COMMIT: "Checks passed · review commit", REVIEW_PUSH: "Local commit saved · review push or local deployment", REVIEW_DEPLOY: "Review deployment", COMPLETED: "Deployment verified live", DEPLOYMENT_STOPPED: "Deployment stopped or expired", DEPLOYMENT_FAILED: "Deployment needs attention", INTERRUPTED: "Interrupted · review retained results", TIMED_OUT: "Time limit reached", FAILED: "Workflow stopped with a failure", CANCELLED: "Cancelled" };
export default function WorkflowPanel({ taskId, selection, onSelect }: { taskId: string; selection: { files: string[]; newFiles: string[]; hash: string } | null; onSelect: (id: string) => void }) {
  const [enabled, setEnabled] = useState(false), [runs, setRuns] = useState<Run[]>([]), [preview, setPreview] = useState<Preview | null>(null);
  const [budget, setBudget] = useState(1), [confirmed, setConfirmed] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState(""), [loadError, setLoadError] = useState("");
  useEffect(() => { setPreview(null); setConfirmed(false); }, [selection?.hash, budget]);
  useEffect(() => {
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout>;
    async function poll() { try { const [status, history] = await Promise.all([api<{ enabled: boolean }>("/workflow/status", undefined, controller.signal), api<Run[]>(`/tasks/${taskId}/workflows`, undefined, controller.signal)]); if (!controller.signal.aborted) { setEnabled(status.enabled); setRuns(history); setLoadError(""); } } catch (e) { if (!controller.signal.aborted) setLoadError((e as Error).message); } if (!controller.signal.aborted) timer = setTimeout(() => void poll(), 2000); }
    void poll(); return () => { controller.abort(); clearTimeout(timer); };
  }, [taskId]);
  async function act(action: "preview" | "start" | "cancel", run?: Run) {
    setBusy(true); setError(""); setConfirmed(false);
    try {
      if (action === "preview" && selection) setPreview(await api<Preview>(`/tasks/${taskId}/workflows/preview`, { files: selection.files, newFiles: selection.newFiles, maxCandidates: budget }));
      else { const result = await api<Run>(action === "cancel" ? `/workflows/${run?.id}/cancel` : `/tasks/${taskId}/workflows`, action === "cancel" ? {} : { input: preview?.input, hash: preview?.hash }); setRuns(old => [result, ...old.filter(r => r.id !== result.id)]); setPreview(null); }
    } catch (e) { setError((e as Error).message); setPreview(null); }
    finally { setBusy(false); }
  }
  const active = runs.some(r => running(r.status));
  if (!enabled && !runs.length && !error && !loadError) return null;
  return <section className="candidate-result workflow-panel" aria-label="Controlled workflow">
    <h3>Controlled workflow <span className="badge neutral">CP8</span></h3>
    <p className="source-note">Review your plan and exact change context first. Authorize a bounded proposal → test → repair loop. Passing output waits for your diff review; commits, pushes and deployments keep their separate confirmations. Closing this dialog leaves authorized work running.</p>
    {!enabled && <p className="source-note">Workflows are disabled for this session; saved results remain available.</p>}
    {(error || loadError) && <p role="alert" className="form-error">{error || loadError}</p>}
    {enabled && selection && !active && <><label className="field-label">Candidate budget<select value={budget} disabled={busy} onChange={e => setBudget(Number(e.target.value))}><option value={1}>1 candidate · no repair</option><option value={2}>2 candidates · up to 1 repair</option><option value={3}>3 candidates · up to 2 repairs</option></select></label><button className="button secondary" disabled={busy} onClick={() => void act("preview")}>Review controlled workflow</button></>}
    {preview && <div className="context-review"><h4>Exact workflow authorization</h4><pre className="context-preview">{preview.prompt}</pre><p className="source-note">{preview.policy}</p><p className="source-note">Pinned image: {preview.imageId}</p><label className="field-label deployment-confirm"><input type="checkbox" disabled={busy} checked={confirmed} onChange={e => setConfirmed(e.target.checked)} /> I reviewed this request and authorize automatic checks and bounded repairs, including sending subsequent redacted failed-check logs and diffs to Codex.</label><button className="button primary" disabled={busy || !confirmed} onClick={() => void act("start")}>Start reviewed controlled workflow</button></div>}
    {runs.slice(0, 3).map(run => <div className="workflow-result" key={run.id}><h4>{labels[run.status] || run.status}</h4><p className="source-note">{run.candidates}/{run.maxCandidates} candidates · execution deadline {new Date(run.deadline).toLocaleTimeString()}</p>{run.error && <p className="form-error">{run.error}</p>}{run.cleanupPending && <p className="form-error">Owned runner cleanup is pending. Open the candidate and retry cleanup before continuing.</p>}<div className="execution-actions">{running(run.status) && <button className="button secondary" disabled={busy || run.status === "STOPPING"} onClick={() => void act("cancel", run)}>Cancel controlled workflow</button>}{run.executionId && <button className="button secondary" onClick={() => onSelect(run.executionId!)}>Open workflow candidate &amp; next steps</button>}</div><details><summary>Workflow events</summary><pre className="check-logs">{run.events}</pre></details></div>)}
  </section>;
}
