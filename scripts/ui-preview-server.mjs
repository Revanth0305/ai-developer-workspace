// Static preview only. No backend proxy, project reads, credentials or write endpoints.
import http from 'node:http';
import { readFile, realpath } from 'node:fs/promises';
import path from 'node:path';
import { isIPv4 } from 'node:net';
const [directory, host] = process.argv.slice(2);
if (!isIPv4(host || '') || !/^(192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host)) throw new Error('Choose this laptop\'s private LAN IPv4 address.');
const root = await realpath(directory);
const stamp = '2026-11-09T12:00:00Z';
const project = { id:'preview-project', name:'Sample website', directory:'Sample project · no local files', instructions:'Sample conventions for visual review only.', createdAt:stamp, updatedAt:stamp };
const tasks = [
  {id:'preview-draft',projectId:project.id,instruction:'Review the mobile layout and note improvements.',status:'DRAFT',createdAt:stamp},
  {id:'preview-plan',projectId:project.id,instruction:'Plan a small landing page with a responsive contact form.',status:'PLANNED',createdAt:stamp},
];
function sample(p) {
  if(p==='/api/health') return {status:'UP',preview:true};
  if(p==='/api/projects') return [project];
  if(p==='/api/tasks') return tasks;
  if(p==='/api/activity') return [{id:'preview-activity',message:'Sample activity · this preview has no backend connection.',createdAt:stamp}];
  if(p==='/api/ai/status') return {enabled:false,authenticated:false,authMode:'',message:'UI preview only. AI requests are disabled.'};
  if(p==='/api/execution/status') return {enabled:false,available:false,profile:'PREVIEW',imageId:'',message:'UI preview only. Execution is disabled.'};
  if(['/api/deployment/status','/api/workflow/status'].includes(p)) return {enabled:false};
  if(p==='/api/projects/preview-project/git') return {repository:false,branch:'',changes:'',commits:'',message:'Sample preview · no Git repository is connected.'};
  if(p==='/api/projects/preview-project/files') return ['README.md'];
  if(p==='/api/projects/preview-project/file') return {content:'# Sample project\n\nThis is sample content for a read-only UI preview. No project files were accessed.'};
  if(p==='/api/projects/preview-project/deployments') return {enabled:false,attempts:[]};
  const match=p.match(/^\/api\/tasks\/(preview-draft|preview-plan)(?:\/(attempts|executions|workflows))?$/);
  if(match) {if(!match[2]) return tasks.find(t=>t.id===match[1]);if(match[2]==='attempts' && match[1]==='preview-plan') return [{id:'sample-plan',taskId:match[1],status:'SUCCEEDED',contextPreview:'Sample context only.',result:'## Sample plan\nCreate a responsive landing page.\n\nThis is illustrative sample data. No AI request was made.',error:'',startedAt:stamp,finishedAt:stamp}];return [];}
  return null;
}
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'};
const server=http.createServer({requestTimeout:5000,headersTimeout:5000,maxHeaderSize:8192},async(req,res)=>{
  const send=(code,type,body)=>{res.writeHead(code,{'Content-Type':type,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; form-action 'none'; object-src 'none'; base-uri 'none'"});res.end(req.method==='HEAD'?undefined:body);};
  if(req.headers.host!==`${host}:4173`) return send(403,'text/plain','Preview host required');
  if(!['GET','HEAD'].includes(req.method))return send(405,'application/json',JSON.stringify({message:'UI preview only: all development and write actions are disabled.'}));
  try {
    const p=decodeURIComponent(new URL(req.url,`http://${host}:4173`).pathname);
    if(p.startsWith('/api/')) {const value=sample(p);return send(value===null?404:200,'application/json',JSON.stringify(value===null?{message:'Not available in UI preview.'}:value));}
    if(p!=='/'&&!p.startsWith('/assets/')&&p!=='/vite.svg')return send(404,'text/plain','Not found');
    const file=await realpath(path.join(root,p==='/'?'index.html':p.slice(1)));
    if(!file.startsWith(root+path.sep))return send(404,'text/plain','Not found');
    send(200,mime[path.extname(file)]||'application/octet-stream',await readFile(file));
  } catch {send(404,'text/plain','Not found');}
});
server.maxConnections=32;
server.listen(4173,host,()=>console.log(`Read-only sample UI preview: http://${host}:4173/`));
