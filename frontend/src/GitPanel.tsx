import { useEffect, useState } from "react";
import DeploymentPanel from "./DeploymentPanel";

type Publication = { id: string; status: string; account: string; repository: string; branch: string; baseCommit: string; treeId: string; commitId: string; authorName: string; authorEmail: string; message: string; diff: string; reviewHash: string; pushHash: string; error: string };
async function api<T>(id: string, action = "", body?: unknown): Promise<T> {
  const response = await fetch(`/api/executions/${id}/git${action}`, { ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(120000) });
  const value = await response.json(); if (!response.ok) throw new Error(value.message || "Git operation failed. Refresh its saved status before retrying."); return value as T;
}
export default function GitPanel({ id }: { id: string }) {
  const [enabled, setEnabled] = useState(false), [publication, setPublication] = useState<Publication | null>(null);
  const [input, setInput] = useState({ account: "", repository: "", authorName: "Codex", authorEmail: "codex@localhost", message: "Apply checked workspace candidate" });
  const [error, setError] = useState(""), [busy, setBusy] = useState(false), [confirmed, setConfirmed] = useState(false);
  useEffect(() => { let alive = true; api<{ enabled: boolean; publication: Partial<Publication> }>(id).then(value => { if (alive) { setEnabled(value.enabled); setPublication(value.publication.id ? value.publication as Publication : null); } }).catch(e => { if (alive) setError(e.message); }); return () => { alive = false; }; }, [id]);
  async function act(action: "preview" | "commit" | "push") {
    setBusy(true); setError(""); setConfirmed(false);
    try { setPublication(await api<Publication>(id, `/${action}`, action === "preview" ? input : { hash: action === "commit" ? publication?.reviewHash : publication?.pushHash })); }
    catch (e) { setError((e as Error).message); const value = await api<{ publication: Partial<Publication> }>(id).catch(() => null); if (value?.publication.id) setPublication(value.publication as Publication); }
    finally { setBusy(false); }
  }
  if (!enabled) return <p className="source-note">Managed-project Git publishing is disabled for this session.</p>;
  return <section className="candidate-result" aria-label="Reviewed Git publishing">
    <h3>Commit &amp; GitHub</h3>
    <p className="source-note">Create a dedicated branch in private storage from the checked candidate. Your original folder, index, branch and remotes stay unchanged. Pushing publishes the commit and its original repository history; review that history and repository visibility first.</p>
    {error && <p role="alert" className="form-error">{error}</p>}
    {!publication && <form onSubmit={e => { e.preventDefault(); void act("preview"); }}>
      {([ ["account", "GitHub account"], ["repository", "GitHub repository name"], ["authorName", "Commit author name"], ["authorEmail", "Commit author email"], ["message", "Commit message"] ] as const).map(([key, label]) => <label className="field-label" key={key}>{label}<input required maxLength={key === "message" ? 500 : 100} value={input[key]} disabled={busy} onChange={e => setInput({ ...input, [key]: e.target.value })} /></label>)}
      <button className="button secondary" disabled={busy}>Review commit &amp; destination</button>
    </form>}
    {publication && <>
      <p><strong>{publication.status}</strong></p>
      <p className="source-note">Destination: github.com/{publication.account}/{publication.repository}<br />Branch: {publication.branch}<br />Parent: {publication.baseCommit}<br />Author: {publication.authorName} &lt;{publication.authorEmail}&gt;<br />Message: {publication.message}</p>
      <pre className="candidate-diff" tabIndex={0}>{publication.diff}</pre>
      {publication.commitId && <p className="source-note">Recorded commit: {publication.commitId}</p>}
      {publication.error && <p className="form-error">{publication.error}</p>}
      {publication.status === "PREPARED" && <div className="execution-actions"><button className="button primary" disabled={busy} onClick={() => void act("commit")}>Commit reviewed candidate locally</button><button className="button secondary" disabled={busy} onClick={() => { setInput({ account: publication.account, repository: publication.repository, authorName: publication.authorName, authorEmail: publication.authorEmail, message: publication.message }); setPublication(null); }}>Edit commit details</button></div>}
      {["COMMITTED", "PUSH_UNKNOWN"].includes(publication.status) && <>
        <label className="field-label"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} /> I reviewed this destination, branch, commit and the history that will be published.</label>
        <button className="button primary" disabled={busy || !confirmed} onClick={() => void act("push")}>{publication.status === "PUSH_UNKNOWN" ? "Reconcile & retry reviewed push" : "Push reviewed branch to GitHub"}</button>
      </>}
      {publication.status === "PUSHED" && <a className="button secondary" href={`https://github.com/${publication.account}/${publication.repository}/commit/${publication.commitId}`} target="_blank" rel="noreferrer">View verified GitHub commit</a>}
    </>}
    {busy && <p role="status" className="source-note">Git operation in progress. Results are recorded; if the connection is interrupted, reopen this candidate before retrying.</p>}
    {publication && ["COMMITTED", "PUSHED", "PUSH_UNKNOWN"].includes(publication.status) && <DeploymentPanel key={id} executionId={id} />}
  </section>;
}
