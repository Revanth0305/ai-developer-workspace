import { useEffect, useState } from "react";
type Deployment = { id: string; executionId: string; taskId: string; status: string; commitId: string; artifactHash: string; imageId: string; manifest: string; reviewHash: string; url: string; logs: string; error: string; cleanupPending: boolean; expiresAt: string | null };
type History = { enabled: boolean; attempts: Deployment[] };
async function api<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api${path}`, { ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}), signal: signal || AbortSignal.timeout(60000) });
  const value = await response.json(); if (!response.ok) throw new Error(value.message || "Deployment operation failed. Review its saved state."); return value as T;
}
function useHistory(path: string) {
  const [history, setHistory] = useState<History>({ enabled: false, attempts: [] }), [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout>;
    async function poll() { try { const value = await api<History>(path, undefined, controller.signal); if (!controller.signal.aborted) { setHistory(value); setError(""); } } catch (e) { if (!controller.signal.aborted) setError((e as Error).message); } if (!controller.signal.aborted) timer = setTimeout(() => void poll(), 3000); }
    void poll(); return () => { controller.abort(); clearTimeout(timer); };
  }, [path]);
  return { history, error };
}
export default function DeploymentPanel({ executionId }: { executionId: string }) {
  const { history, error: loadError } = useHistory(`/executions/${executionId}/deployments`);
  const [current, setCurrent] = useState<Deployment | null>(null), [selectedId, setSelectedId] = useState("");
  const [busy, setBusy] = useState(false), [confirmed, setConfirmed] = useState(false), [error, setError] = useState("");
  useEffect(() => { const selected = history.attempts.find(d => d.id === selectedId) || history.attempts[0]; if (selected) setCurrent(selected); }, [history.attempts, selectedId]);
  async function act(action: "preview" | "start" | "stop" | "refresh" | "cleanup") {
    setBusy(true); setError(""); setConfirmed(false);
    try { const value = await api<Deployment>(action === "preview" ? `/executions/${executionId}/deployments/preview` : `/deployments/${current?.id}/${action}`, action === "start" ? { hash: current?.reviewHash } : {}); setCurrent(value); setSelectedId(value.id); }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <section className="candidate-result deployment-panel" aria-label="Local test deployment">
    <h3>Local test deployment <span className="badge neutral">CP7</span></h3>
    <p className="source-note">Deploy the checked private commit’s public/ assets to a temporary localhost URL. This fixed static profile uses separate HTML, CSS and JavaScript files, no package installation or user secrets. External requests and inline scripts/styles are blocked. One live deployment at a time; 30-minute lease.</p>
    {!history.enabled && <p className="source-note">Local test deployment is disabled for this session.</p>}
    {(error || loadError) && <p role="alert" className="form-error">{error || loadError}</p>}
    {history.enabled && history.attempts.length < 3 && <button className="button secondary" disabled={busy || current?.status === "DEPLOYING"} onClick={() => void act("preview")}>Review local deployment</button>}
    {history.attempts.length > 0 && <label className="field-label">Deployment history ({history.attempts.length}/3)<select value={selectedId || current?.id || ""} onChange={e => { setSelectedId(e.target.value); setConfirmed(false); }}>{history.attempts.map((d, index) => <option key={d.id} value={d.id}>Deployment {history.attempts.length - index} · {d.status}</option>)}</select></label>}
    {current && <>
      <p className="source-note"><strong>{current.status}</strong><br />Commit: {current.commitId}<br />Artifact: {current.artifactHash}<br />Image: {current.imageId}<br />Target: this computer · 127.0.0.1 · an available Docker-assigned port</p>
      <details open={current.status === "REVIEW"}><summary>Reviewed static artifact</summary><pre className="context-preview">{current.manifest}</pre></details>
      {current.error && <p className="form-error">{current.error}</p>}
      {current.expiresAt && <p className="source-note">Lease ends {new Date(current.expiresAt).toLocaleString()}</p>}
      {current.status === "REVIEW" && history.enabled && <>
        <label className="field-label deployment-confirm"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} /> I reviewed this commit and public assets for confidential content and want a local test URL.</label>
        <button className="button primary" disabled={busy || !confirmed} onClick={() => void act("start")}>Deploy reviewed local test site</button>
      </>}
      <div className="execution-actions">
        {current.status === "LIVE" && current.url && <><a className="button primary" href={current.url} target="_blank" rel="noreferrer">Open verified test site</a><button className="button secondary" disabled={busy} onClick={() => void act("refresh")}>Refresh health &amp; logs</button></>}
        {["LIVE", "DEPLOYING", "REVIEW"].includes(current.status) && <button className="button secondary" disabled={busy} onClick={() => void act("stop")}>{current.status === "DEPLOYING" ? "Cancel deployment" : current.status === "REVIEW" ? "Cancel review" : "Stop local deployment"}</button>}
        {current.cleanupPending && !["DEPLOYING", "STOPPING"].includes(current.status) && <button className="button secondary" disabled={busy} onClick={() => void act("cleanup")}>Retry deployment cleanup</button>}
      </div>
      {current.logs && <details><summary>Actual deployment logs</summary><pre className="check-logs">{current.logs}</pre></details>}
    </>}
    {busy && <p role="status" className="source-note">Saving the reviewed deployment operation…</p>}
  </section>;
}
export function DeploymentSummary({ projectId, onOpen }: { projectId: string; onOpen: (taskId: string) => void }) {
  const { history, error } = useHistory(`/projects/${projectId}/deployments`);
  return <section className="panel"><div className="panel-heading"><h2>Test deployments</h2><span className="badge neutral">Local Docker</span></div>
    <p className="source-note">{history.enabled ? "Review a passing candidate’s local commit to prepare a static test site." : "Local deployment is disabled for this session."}</p>
    {error && <p className="form-error">{error}</p>}
    {history.attempts.slice(0, 3).map(d => <button key={d.id} className="deployment-summary-row" onClick={() => onOpen(d.taskId)}><span><strong>{d.status}</strong><small>{d.commitId.slice(0, 12)}</small></span><span>Review →</span></button>)}
  </section>;
}
