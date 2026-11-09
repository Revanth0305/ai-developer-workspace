// Trusted static-only runtime. Project files are data; no project server code is imported.
import http from 'node:http';
import { readFile, realpath } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
const config = JSON.parse(await readFile('/site/deployment.json', 'utf8'));
const root = await realpath('/site/public');
const allowed = new Set(config.files);
const digest = createHash('sha256').update(config.commit);
for (const name of [...allowed].sort()) {
  const key = Buffer.from(name, 'utf8'), value = await readFile(path.join(root, name));
  const sizes = Buffer.alloc(8); sizes.writeInt32BE(key.length, 0); sizes.writeInt32BE(value.length, 4);
  digest.update(sizes).update(key).update(value);
}
const artifact = digest.digest('hex');
if (artifact !== config.artifact) throw new Error('Copied artifact did not match its reviewed fingerprint');
const mime = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.mjs': 'text/javascript', '.json': 'application/json', '.txt': 'text/plain' };
const server = http.createServer({ requestTimeout: 5000, headersTimeout: 5000, maxHeaderSize: 8192 }, async (req, res) => {
  const send = (code, type, body) => {
    res.writeHead(code, { 'Content-Type': type + '; charset=utf-8', 'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'none'; form-action 'none'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'" });
    res.end(req.method === 'HEAD' ? undefined : body);
    console.log(`HTTP ${code}`);
  };
  try {
    if (!/^127\.0\.0\.1:[0-9]+$/.test(req.headers.host || '') || req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) return send(403, 'text/plain', 'Local deployment origin required');
    if (!['GET', 'HEAD'].includes(req.method)) return send(405, 'text/plain', 'Read-only static deployment');
    const name = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname);
    if (name === '/__jarvis_health') return send(200, 'application/json', JSON.stringify({ id: config.id, commit: config.commit, artifact, expiresAt: config.expiresAt }));
    const relative = name === '/' ? 'index.html' : name.slice(1);
    if (!allowed.has(relative) || relative.includes('\\') || relative.split('/').some(part => part.startsWith('.'))) return send(404, 'text/plain', 'Asset not found');
    const file = await realpath(path.join(root, relative));
    if (!file.startsWith(root + path.sep)) return send(404, 'text/plain', 'Asset not found');
    return send(200, mime[path.extname(file)] || 'text/plain', await readFile(file));
  } catch { return send(404, 'text/plain', 'Asset not found'); }
});
server.maxConnections = 32;
server.maxHeadersCount = 40;
server.keepAliveTimeout = 3000;
server.listen(8080, '0.0.0.0', () => console.log(`Static deployment ready: ${config.id} ${config.commit}`));
// Expiry is enforced even while Jarvis is stopped.
setTimeout(() => { console.log('Deployment lease expired'); server.close(); setTimeout(() => process.exit(0), 1000); }, Math.max(0, Math.min(1800000, Date.parse(config.expiresAt) - Date.now())));
