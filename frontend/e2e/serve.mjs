// Serves the production build as a static SPA without a backend:
//  - existing files from dist/treasury-kompass/browser,
//  - /api/* and /agent/* answered with 404 and a non-JSON body (no backend behind them),
//  - every other path without a file extension falls back to index.html (SPA routes),
//  - a missing file with an extension is a 404, so a broken asset reference shows up in the tests.
//
// Usage: node serve.mjs [--port 4400] [--root ../dist/treasury-kompass/browser]
import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const here = fileURLToPath(new URL('.', import.meta.url));
const { values } = parseArgs({
  options: {
    port: { type: 'string', default: process.env.PORT ?? '4400' },
    root: { type: 'string', default: join(here, '..', 'dist', 'treasury-kompass', 'browser') },
  },
});

const root = resolve(values.root);
const port = Number(values.port);

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.ico': 'image/x-icon',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
};

const BACKEND_PREFIXES = ['/api/', '/agent/'];

/** A plain-text error page, not JSON: what the app must never mistake for a backend answer. */
function notFound(res) {
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end('The page could not be found\n\nNOT_FOUND\n');
}

async function fileAt(urlPath) {
  const candidate = normalize(join(root, decodeURIComponent(urlPath)));
  // Reject anything that escapes the build folder (e.g. /../../etc/passwd).
  if (candidate !== root && !candidate.startsWith(root + sep)) return null;
  try {
    const info = await stat(candidate);
    if (info.isFile()) return candidate;
    if (info.isDirectory()) {
      const index = join(candidate, 'index.html');
      return (await stat(index).catch(() => null))?.isFile() ? index : null;
    }
  } catch {
    // Not found: the caller decides between SPA fallback and 404.
  }
  return null;
}

function send(req, res, file) {
  const type = CONTENT_TYPES[extname(file).toLowerCase()] ?? 'application/octet-stream';
  const isIndex = file.endsWith(`${sep}index.html`);
  res.writeHead(200, {
    'Content-Type': type,
    // Hashed bundles may be cached; index.html never, so a fresh build is picked up immediately.
    'Cache-Control': isIndex ? 'no-cache' : 'public, max-age=31536000, immutable',
  });
  if (req.method === 'HEAD') {
    res.end();
    return;
  }
  createReadStream(file).pipe(res);
}

const server = createServer(async (req, res) => {
  const path = new URL(req.url ?? '/', 'http://localhost').pathname;

  if (BACKEND_PREFIXES.some((prefix) => path === prefix.slice(0, -1) || path.startsWith(prefix))) {
    notFound(res);
    return;
  }

  let file;
  try {
    file = await fileAt(path);
  } catch {
    notFound(res); // e.g. malformed percent-encoding
    return;
  }
  if (file) {
    send(req, res, file);
    return;
  }
  if (extname(path)) {
    notFound(res);
    return;
  }
  send(req, res, join(root, 'index.html'));
});

const index = await stat(join(root, 'index.html')).catch(() => null);
if (!index?.isFile()) {
  console.error(`serve.mjs: ${join(root, 'index.html')} not found. Build the app first.`);
  process.exit(1);
}

server.listen(port, '127.0.0.1', () => {
  console.log(`serve.mjs: serving ${root} on http://127.0.0.1:${port}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
