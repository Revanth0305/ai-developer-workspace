import type { FormEvent, ReactNode } from "react";
import "./HolographicOverview.css";
import { uiPreview } from "./previewMode";

type Project = { id: string; name: string };
type Task = { id: string; instruction: string; status: string; projectId: string };
type Props = {
  projects: Project[]; tasks: Task[]; selectedId: string; instruction: string; saving: boolean;
  connected: boolean; runnerReady: boolean;
  onProject: (id: string) => void; onInstruction: (value: string) => void;
  onSubmit: (event: FormEvent) => void; onAdd: () => void; onContinue: () => void;
  onTask: (id: string) => void; onWorkflow: (kind: "plan" | "execution" | "git") => void;
};

export function HoloIcon({ name, size = 22 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    spark: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5Z" /><path d="M21 2v4m-2-2h4" /></>,
    plan: <><path d="M8 16c0-3-3-3-3-7a7 7 0 0 1 14 0c0 4-3 4-3 7M8 17h8m-7 3h6m-5 2h4" /><path d="M12 5v6" /></>,
    code: <path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-14-2 16" />,
    test: <><path d="M9 2h6m-5 0v7L3 20q-1 2 2 2h14q3 0 2-2L14 9V2M7 15h10" /><circle cx="11" cy="17" r=".6" /></>,
    cube: <><path d="m12 2 10 5v10l-10 5-10-5V7Zm0 10 10-5M12 12 2 7m10 5v10" /></>,
    branch: <><circle cx="6" cy="4" r="2" /><circle cx="18" cy="7" r="2" /><circle cx="6" cy="20" r="2" /><path d="M6 6v12m0-5c8 0 12-1 12-4" /></>,
    chart: <><path d="M4 20V12h3v8Zm7 0V7h3v13Zm7 0V2h3v18Z" /></>,
    plus: <><rect x="3" y="3" width="18" height="18" rx="4" /><path d="M12 7v10M7 12h10" /></>,
    play: <path d="m7 3 15 9-15 9Z" />,
    send: <><path d="m3 3 19 9-19 9 4-9Zm4 9h15" /></>,
    focus: <><circle cx="11" cy="13" r="8" /><circle cx="11" cy="13" r="4" /><path d="m11 13 10-10m-5 0h5v5" /></>,
    file: <><path d="M6 2h9l5 5v15H4V2Zm9 0v6h5M8 13h8m-8 4h6" /></>,
    arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.spark}</svg>;
}

function Observatory() {
  return <div className="observatory" aria-hidden="true">
    <div className="ceiling-ring" /><div className="room-strut strut-left" /><div className="room-strut strut-right" />
    <div className="city-window window-left" /><div className="city-window window-right" />
    <div className="cityscape">{Array.from({ length: 38 }, (_, i) => <i key={i} style={{ left: `${i * 2.75}%`, height: `${24 + ((i * 37) % 95)}px`, width: `${14 + ((i * 13) % 24)}px`, opacity: .25 + (i % 4) * .12 }} />)}</div>
    <div className="observatory-floor" /><div className="floor-rail rail-cyan" /><div className="floor-rail rail-violet" />
  </div>;
}

function JarvisHologram() {
  return <svg className="jarvis-hologram" viewBox="0 0 560 500" fill="none" aria-hidden="true">
    <defs>
      <linearGradient id="holo-rim" x1="140" y1="90" x2="410" y2="330" gradientUnits="userSpaceOnUse"><stop stopColor="#b3a2ff" /><stop offset=".22" stopColor="#34bfff" /><stop offset=".56" stopColor="#6046ee" /><stop offset=".8" stopColor="#39e5ff" /><stop offset="1" stopColor="#b285ff" /></linearGradient>
      <radialGradient id="holo-head" cx=".25" cy=".18" r=".9"><stop stopColor="#306da4" /><stop offset=".24" stopColor="#132c51" /><stop offset=".56" stopColor="#071627" /><stop offset=".82" stopColor="#080e27" /><stop offset="1" stopColor="#3654ac" /></radialGradient>
      <radialGradient id="holo-face" cx=".5" cy=".28" r=".7"><stop stopColor="#0f2944" /><stop offset=".6" stopColor="#061021" /><stop offset="1" stopColor="#14294b" /></radialGradient>
      <linearGradient id="holo-ear" x1="0" y1="0" x2="1" y2=".6"><stop stopColor="#13163c" /><stop offset=".4" stopColor="#7355ff" /><stop offset=".65" stopColor="#b59bff" /><stop offset="1" stopColor="#2c378e" /></linearGradient>
      <linearGradient id="holo-beam" x1="280" y1="285" x2="280" y2="435" gradientUnits="userSpaceOnUse"><stop stopColor="#2e6dff" stopOpacity="0" /><stop offset="1" stopColor="#18dfff" stopOpacity=".4" /></linearGradient>
      <linearGradient id="holo-base" x1="280" y1="411" x2="280" y2="475" gradientUnits="userSpaceOnUse"><stop stopColor="#24316a" /><stop offset=".4" stopColor="#09162b" /><stop offset=".75" stopColor="#102d4c" /><stop offset="1" stopColor="#040a1b" /></linearGradient>
      <radialGradient id="holo-aura"><stop stopColor="#1877ff" stopOpacity=".3" /><stop offset="1" stopColor="#2b50ff" stopOpacity="0" /></radialGradient>
      <pattern id="holo-hex" width="26" height="23" patternUnits="userSpaceOnUse"><path d="m0 6 6.5-4 13 0 6.5 4v11l-6.5 4h-13L0 17Zm26 0 6-4" stroke="#63b9f6" strokeWidth=".6" opacity=".23" /></pattern>
      <filter id="holo-glow" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="5" /></filter>
      <filter id="holo-soft" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="16" /></filter>
      <clipPath id="holo-mask"><path d="M280 82c-72 0-115 47-115 111 0 81 39 133 115 142 76-9 115-61 115-142 0-64-43-111-115-111Z" /></clipPath>
    </defs>
    <ellipse cx="280" cy="218" rx="212" ry="211" fill="url(#holo-aura)" />
    <g opacity=".45" stroke="#4398ee"><ellipse cx="280" cy="240" rx="230" ry="61" transform="rotate(-16 280 240)" /><ellipse cx="280" cy="240" rx="238" ry="63" transform="rotate(19 280 240)" strokeDasharray="2 7" /></g>
    <g className="projection-rays"><path d="M175 292 258 432h44l83-140Z" fill="url(#holo-beam)" /><path d="m200 315 66 115m-32-104 39 104m93-115-69 115m32-104-39 104" stroke="#2fdcff" strokeOpacity=".3" /><path d="M280 328v100" stroke="#46e6ff" strokeWidth="2" opacity=".6" /></g>
    <g transform="translate(-47.6 -34.85) scale(1.17)"><g className="hologram-float">
      <path d="M157 212C140 46 413 44 403 212" stroke="#754bff" strokeWidth="17" filter="url(#holo-glow)" />
      <path d="M157 212C140 46 413 44 403 212" stroke="url(#holo-rim)" strokeWidth="10" />
      <path d="M160 210C142 52 408 51 400 210" stroke="#c1eaff" strokeWidth="2" />
      <rect x="143" y="178" width="35" height="103" rx="17" fill="url(#holo-ear)" stroke="#9e78ff" strokeWidth="2" />
      <rect x="382" y="178" width="35" height="103" rx="17" fill="url(#holo-ear)" stroke="#8c6dff" strokeWidth="2" />
      <path d="M151 196v68m258-68v68" stroke="#c6b9ff" strokeWidth="3" filter="url(#holo-glow)" />
      <path d="M280 82c-72 0-115 47-115 111 0 81 39 133 115 142 76-9 115-61 115-142 0-64-43-111-115-111Z" fill="url(#holo-head)" stroke="url(#holo-rim)" strokeWidth="3" />
      <g clipPath="url(#holo-mask)">
        <path d="M280 82c-72 0-115 47-115 111 0 81 39 133 115 142 76-9 115-61 115-142 0-64-43-111-115-111Z" fill="url(#holo-hex)" />
        <path d="m168 172 45-63 67-23 67 23 45 63-26-32-49-30-37-9-37 9-49 30Z" fill="#7dc7ff" opacity=".15" />
        <path d="m186 167 36-35 58-15 58 15 36 35-17 102-77 48-77-48Z" fill="url(#holo-face)" stroke="#4487bd" strokeOpacity=".4" />
        <path d="m185 174 18 95 77 48 77-48 18-95m-146-34 51-13 51 13" stroke="#73bdf0" opacity=".25" />
        <path d="m213 281 28 12m78 0 28-12m-88 18h42" stroke="#54a7f0" opacity=".45" strokeWidth="2" />
        <path d="m263 160 17 12 17-12m-17 12v44" stroke="#2c5278" />
        <path d="M192 198q25-13 68 14M300 212q43-27 68-14" stroke="#02101e" strokeWidth="14" strokeLinecap="round" />
        <path d="M201 205q26-2 53 15-31 13-51-2-7-5-2-13ZM359 205q-26-2-53 15 31 13 51-2 7-5 2-13Z" fill="#22dfff" filter="url(#holo-glow)" />
        <path d="M201 205q26-2 53 15-31 13-51-2-7-5-2-13ZM359 205q-26-2-53 15 31 13 51-2 7-5 2-13Z" fill="#66efff" />
        <path d="M202 205q24-1 46 13m110-13q-24-1-46 13" stroke="#d3ffff" strokeWidth="2" />
        <ellipse cx="209" cy="117" rx="24" ry="46" transform="rotate(48 209 117)" fill="#72c8ff" opacity=".09" />
      </g>
      <path d="M169 201c0-63 38-112 103-117M172 257c15 45 51 69 97 77" stroke="#57ceff" strokeOpacity=".7" strokeWidth="2" />
      <path d="M385 259c-13 43-49 67-96 75" stroke="#ad8bff" strokeWidth="3" filter="url(#holo-glow)" />
    </g></g>
    <ellipse cx="280" cy="438" rx="206" ry="40" fill="#1488ff" opacity=".18" filter="url(#holo-soft)" />
    <path d="M77 427v19c0 24 91 43 203 43s203-19 203-43v-19Z" fill="url(#holo-base)" stroke="#17466c" />
    <ellipse cx="280" cy="427" rx="203" ry="39" fill="#0b172d" stroke="#3565a7" strokeWidth="2" />
    <ellipse cx="280" cy="426" rx="180" ry="30" stroke="url(#holo-rim)" strokeWidth="5" filter="url(#holo-glow)" />
    <ellipse cx="280" cy="426" rx="180" ry="30" stroke="url(#holo-rim)" strokeWidth="3" />
    <ellipse cx="280" cy="424" rx="150" ry="23" fill="#10203d" stroke="#3658b2" strokeWidth="2" />
    <ellipse cx="280" cy="424" rx="121" ry="17" stroke="#3bbfff" strokeWidth="2" strokeDasharray="112 17 34 10" />
    <ellipse cx="280" cy="424" rx="77" ry="11" fill="#0b8fff" opacity=".32" />
    <ellipse cx="280" cy="424" rx="52" ry="7" fill="#55e8ff" opacity=".85" filter="url(#holo-glow)" />
    <path d="M217 471h126m-214-25 28 7m247-7-28 7" stroke="#34dfff" strokeWidth="4" strokeLinecap="round" />
    <g className="holo-particles" fill="#7aeaff"><circle cx="235" cy="365" r="1.5" /><circle cx="308" cy="356" r="2" /><circle cx="283" cy="389" r="1.4" /><circle cx="253" cy="403" r="1" /><circle cx="338" cy="377" r="1.4" /><circle cx="204" cy="337" r="1.2" /><circle cx="355" cy="335" r="1.2" /></g>
  </svg>;
}

export default function HolographicOverview(p: Props) {
  const selected = p.projects.find(project => project.id === p.selectedId);
  const tasks = p.tasks.filter(task => task.projectId === p.selectedId);
  const focus = tasks.filter(task => task.status !== "CANCELLED").slice(0, 3);
  const pulse = [
    { label: "Connected projects", count: p.projects.length, tone: "mint" },
    { label: "Planning now", count: p.tasks.filter(task => task.status === "PLANNING").length, tone: "cyan" },
    { label: "Draft instructions", count: p.tasks.filter(task => task.status === "DRAFT").length, tone: "violet" },
    { label: "Plans ready", count: p.tasks.filter(task => task.status === "PLANNED").length, tone: "amber" },
  ];
  const plan = (prompt: string) => { p.onInstruction(prompt); document.getElementById("jarvis-command")?.focus(); };
  const orbit = [
    { label: "Build", icon: "code", action: () => p.onWorkflow("execution") },
    { label: "Test", icon: "test", action: () => p.onWorkflow("execution") },
    { label: "Git", icon: "branch", action: () => p.onWorkflow("git") },
    { label: "Plan", icon: "plan", action: () => plan("Plan a new feature: ") },
    { label: "Manage", icon: "cube", action: () => { window.location.hash = "projects"; } },
    { label: "Monitor", icon: "chart", action: () => { window.location.hash = "workspace"; } },
  ];
  return <section className="holo-overview" aria-label="Jarvis command center">
    <Observatory />
    <header className="holo-greeting"><p className="holo-kicker"><span /> YOUR PERSONAL COMMAND CENTER</p><h1>Hello Revanth, I’m <span>Jarvis</span></h1><p>Your AI partner to build, automate, and solve — one reviewed step at a time.</p></header>
    <div className="holo-stage-grid">
      <section className="holo-glass project-pulse"><div className="holo-card-heading"><HoloIcon name="spark" /><h2>Project Pulse</h2><span className="live-dot" title="Saved workspace data" /></div>
        <div className="pulse-rows">{pulse.map(item => <a href={item.label === "Connected projects" ? "#projects" : "#history"} className="pulse-row" key={item.label}><span className={`pulse-dot ${item.tone}`} /><span>{item.label}</span><strong>{item.count}</strong></a>)}</div>
        <p className="holo-card-foot">{p.tasks.length} saved {p.tasks.length === 1 ? "instruction" : "instructions"}<span>LOCAL WORKSPACE</span></p>
      </section>
      <div className="holo-stage"><div className="holo-core-caption"><span className={`signal-dot ${p.connected ? "ready" : ""}`} />{p.connected ? "CODEX CONNECTED" : "LOCAL INTERFACE READY"}</div>
        <svg className="orbit-connections" viewBox="0 0 700 388" preserveAspectRatio="none" fill="none" aria-hidden="true"><g stroke="#3978bd" strokeWidth=".8" opacity=".45"><path d="M65 77c78 0 106 33 207 51M45 180c90-4 145 6 217 28M65 283c95 0 130-17 215-29M635 77c-78 0-106 33-207 51M655 180c-90-4-145 6-217 28M635 283c-95 0-130-17-215-29" /></g></svg>
        <JarvisHologram />
        <div className="orbit-actions" aria-label="Workspace capabilities">{orbit.map((item, index) => <button type="button" className={`orbit-action orbit-${index}`} key={item.label} onClick={item.action} disabled={!selected && !["Manage", "Monitor"].includes(item.label)} title={item.label === "Git" ? "Open checked candidates and reviewed Git publishing" : `${item.label} in your workspace`}><HoloIcon name={item.icon} size={27} /><span>{item.label}</span></button>)}</div>
        <p className="holo-stage-caption"><span />{p.runnerReady ? "ISOLATED CHECKS AVAILABLE" : "REVIEWED PLANNING WORKSPACE"}<span /></p>
      </div>
      <section className="holo-glass today-focus"><div className="holo-card-heading"><HoloIcon name="focus" /><h2>Today’s Focus</h2><a href="#history" aria-label="View all instructions"><HoloIcon name="arrow" size={17} /></a></div>
        {focus.length ? <div className="focus-items">{focus.map(task => <button type="button" className="focus-item" onClick={() => p.onTask(task.id)} key={task.id}><span className={`focus-symbol ${task.status.toLowerCase()}`}><HoloIcon name={task.status === "PLANNING" ? "spark" : "file"} size={17} /></span><span><strong title={task.instruction}>{task.instruction}</strong><small>{task.status === "PLANNED" ? "Plan ready for review" : task.status === "PLANNING" ? "Planning in progress" : task.status === "FAILED" ? "Needs your attention" : "Draft · ready to continue"}</small></span><span className="focus-chevron">›</span></button>)}</div> : <div className="focus-empty"><HoloIcon name="focus" size={33} /><p>A clear space.<br /><strong>A fresh possibility.</strong></p><span>Your next instruction starts below.</span></div>}
        <a className="focus-footer" href="#history">Open task history <HoloIcon name="arrow" size={14} /></a>
      </section>
    </div>
    <div className="holo-command-area"><div className="command-project"><span><span className="pulse-dot cyan" />{selected ? "WORKING IN" : "YOUR WORKSPACE"}</span>{selected ? <select aria-label="Command project" value={p.selectedId} onChange={event => p.onProject(event.target.value)}>{p.projects.map(project => <option value={project.id} key={project.id}>{project.name}</option>)}</select> : <button type="button" onClick={p.onAdd}>Connect your first project <HoloIcon name="arrow" size={13} /></button>}</div>
      <form className="holo-command" onSubmit={event => { if (uiPreview) event.preventDefault(); else p.onSubmit(event); }}><span className="command-spark"><HoloIcon name="spark" size={26} /></span><label className="sr-only" htmlFor="jarvis-command">Task instruction</label><textarea id="jarvis-command" rows={1} value={p.instruction} disabled={!selected || p.saving} onChange={event => p.onInstruction(event.target.value)} onKeyDown={event => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }} maxLength={12000} required placeholder={selected ? "Ask Jarvis to plan, build, or investigate…" : "Connect a project to start creating…"} /><span className="command-divider" /><button type="submit" className="command-send" disabled={uiPreview || !selected || !p.instruction.trim() || p.saving} aria-label={p.saving ? "Saving instruction" : "Save instruction draft"}><HoloIcon name="send" size={25} /></button></form>
      <p className="command-reassurance">{uiPreview ? "UI preview · Try typing here. Saving and development actions are disabled." : "Saved as a draft. You review the context before Codex starts."}</p>
    </div>
    <div className="holo-quick-actions" aria-label="Quick actions">
      <button type="button" onClick={p.onAdd}><HoloIcon name="plus" /><span>Connect a project</span></button>
      <button type="button" onClick={p.onContinue} disabled={!tasks.length}><HoloIcon name="play" /><span>Continue last work</span></button>
      <button type="button" disabled={!selected} onClick={() => plan("Plan a new feature: ")}><HoloIcon name="plan" /><span>Plan a feature</span></button>
      <button type="button" disabled={!selected} onClick={() => plan("Review the project for ")}><HoloIcon name="spark" /><span>Review code</span></button>
      <button type="button" onClick={() => { window.location.hash = "workspace"; }}><HoloIcon name="chart" /><span>Get project update</span></button>
    </div>
    <div className="holo-shortcut"><kbd>Ctrl K</kbd><span>Bring your next idea to Jarvis</span><i /> <span className="local-signature">YOUR MACHINE. YOUR CONTROL.</span></div>
  </section>;
}
