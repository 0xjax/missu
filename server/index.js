import { parseDirectory, parseHome, parseVideoDetail, parseVideoList } from '../src/api/parse.ts';

const UPSTREAM = process.env.UPSTREAM_URL || 'https://missav.ai';
const FLARESOLVERR_URL = process.env.FLARESOLVERR_URL;
const HOST = process.env.HOST || '127.0.0.1';
const PORT = Number(process.env.PORT || 3000);
const PUBLIC_HTTPS = process.env.PUBLIC_HTTPS === 'true';
const BROWSER_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36';
const CACHEABLE_GET = /^\/(?:$|new$|release$|fc2$|today-hot$|weekly-hot$|monthly-hot$|genres(?:\/|$)|actresses(?:\/|$)|makers(?:\/|$)|search\/|[a-z0-9][a-z0-9-]*$)/i;
const cache = new Map();
const inFlight = new Map();
let nextUpstreamAt = 0;
let flareSessionReady;
let upstreamUserAgent = BROWSER_UA;

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function ttlFor(path) {
  if (/^\/(?:genres|actresses|makers)(?:\/|$)/.test(path)) return 10 * 60_000;
  return 60_000;
}

function cookiesFrom(headers) {
  return typeof headers.getSetCookie === 'function'
    ? headers.getSetCookie()
    : headers.get('set-cookie')
      ? [headers.get('set-cookie')]
      : [];
}

function normalizeCookie(cookie) {
  let value = cookie.replace(/;\s*Domain=[^;]+/gi, '');
  if (!PUBLIC_HTTPS) {
    value = value.replace(/;\s*Secure/gi, '').replace(/;\s*SameSite=None/gi, '');
  }
  return value;
}

function makeResponse({ body, status, contentType, cookies = [] }, cacheState) {
  const headers = new Headers({
    'content-type': contentType || 'text/html; charset=utf-8',
    'x-bff-cache': cacheState,
  });
  for (const cookie of cookies) headers.append('set-cookie', normalizeCookie(cookie));
  const responseBody = body instanceof ArrayBuffer ? body.slice(0) : body;
  return new Response(responseBody, { status, headers });
}

async function scheduleUpstream() {
  const now = Date.now();
  const wait = Math.max(0, nextUpstreamAt - now);
  nextUpstreamAt = Math.max(now, nextUpstreamAt) + 400;
  if (wait > 0) await delay(wait);
}

function upstreamHeaders(request) {
  const headers = new Headers({
    accept: request.headers.get('accept') || 'text/html,application/xhtml+xml',
    'user-agent': upstreamUserAgent,
  });
  for (const name of ['content-type', 'cookie', 'x-xsrf-token']) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  return headers;
}

async function directUpstream(request, target) {
  await scheduleUpstream();
  return fetch(target, {
    method: request.method,
    headers: upstreamHeaders(request),
    body: request.method === 'GET' ? undefined : await request.arrayBuffer(),
    redirect: 'follow',
    signal: AbortSignal.timeout(20_000),
  });
}

async function ensureFlareSession() {
  if (!flareSessionReady) {
    flareSessionReady = fetch(FLARESOLVERR_URL, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ cmd: 'sessions.create', session: 'missav-bff' }),
      signal: AbortSignal.timeout(20_000),
    }).then((response) => {
      if (!response.ok) throw new Error(`FlareSolverr HTTP ${response.status}`);
    }).catch((error) => {
      flareSessionReady = undefined;
      throw error;
    });
  }
  return flareSessionReady;
}

async function recoverWithBrowser(request, target) {
  if (!FLARESOLVERR_URL || request.method !== 'GET') return null;
  await ensureFlareSession();
  const cookie = request.headers.get('cookie');
  const cookies = cookie
    ? cookie.split(';').map((part) => {
      const index = part.indexOf('=');
      return { name: part.slice(0, index).trim(), value: part.slice(index + 1).trim() };
    }).filter((item) => item.name)
    : undefined;
  const response = await fetch(FLARESOLVERR_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      cmd: 'request.get',
      url: target,
      session: 'missav-bff',
      cookies,
      disableMedia: true,
      maxTimeout: 60_000,
    }),
    signal: AbortSignal.timeout(70_000),
  });
  if (!response.ok) throw new Error(`FlareSolverr HTTP ${response.status}`);
  const result = await response.json();
  if (result.status !== 'ok' || !result.solution) throw new Error('浏览器恢复失败');
  const solution = result.solution;
  if (solution.userAgent) upstreamUserAgent = solution.userAgent;
  return {
    body: solution.response,
    status: solution.status,
    contentType: solution.headers?.['content-type'],
    cookies: solution.cookies?.map((item) => `${item.name}=${item.value}; Path=${item.path || '/'}`) ?? [],
  };
}

async function fetchPublic(request, target) {
  let upstream;
  try {
    upstream = await directUpstream(request, target);
    if (upstream.status === 403) {
      await delay(750);
      upstream = await directUpstream(request, target);
    }
    if (upstream.status === 403) {
      const recovered = await recoverWithBrowser(request, target);
      if (recovered) return recovered;
    }
  } catch (error) {
    const recovered = await recoverWithBrowser(request, target);
    if (recovered) return recovered;
    throw error;
  }
  return {
    body: await upstream.arrayBuffer(),
    status: upstream.status,
    contentType: upstream.headers.get('content-type'),
    cookies: cookiesFrom(upstream.headers),
  };
}

function parseContent(path, html) {
  const url = new URL(path, UPSTREAM);
  const pathname = url.pathname;
  const page = Number(url.searchParams.get('page') || 1);

  if (pathname === '/') return { kind: 'home', data: parseHome(html) };
  if (pathname === '/saved') throw new Error('unsupported path');
  if (pathname.startsWith('/search/') || /^\/(?:new|release|fc2|today-hot|weekly-hot|monthly-hot)$/.test(pathname)) {
    return { kind: 'videos', data: parseVideoList(html, page) };
  }
  if (/^\/(?:genres|actresses|makers)(?:\/|$)/.test(pathname)) {
    const kind = pathname.includes('/actresses') ? 'actresses' : pathname.includes('/makers') ? 'makers' : 'genres';
    return { kind: 'directory', data: parseDirectory(html, kind, page) };
  }
  const id = pathname.split('/').filter(Boolean).at(-1);
  if (!id) throw new Error('unsupported path');
  return { kind: 'detail', data: parseVideoDetail(html, id) };
}

async function contentResponse(request, path) {
  const target = new URL(path, UPSTREAM).toString();
  const pathname = new URL(target).pathname;
  if (!inFlight.has(target)) {
    inFlight.set(target, publicRequest(request, target, pathname).finally(() => inFlight.delete(target)));
  }
  const { entry, cacheState } = await inFlight.get(target);
  if (entry.status !== 200) {
    return Response.json({ message: '上游暂时不可用，请稍后重试' }, { status: 502 });
  }
  const html = typeof entry.body === 'string' ? entry.body : new TextDecoder().decode(entry.body);
  return Response.json(parseContent(path, html), { headers: { 'x-bff-cache': cacheState } });
}

async function publicRequest(request, target, path) {
  const cached = cache.get(target);
  if (cached && cached.expiresAt > Date.now()) {
    return { entry: cached, cacheState: 'hit' };
  }
  try {
    const result = await fetchPublic(request, target);
    if (result.status === 200) {
      const entry = { ...result, expiresAt: Date.now() + ttlFor(path) };
      cache.set(target, entry);
      return { entry, cacheState: 'miss' };
    }
    if (cached) return { entry: cached, cacheState: 'stale' };
    return { entry: result, cacheState: 'error' };
  } catch (error) {
    if (cached) return { entry: cached, cacheState: 'stale' };
    throw error;
  }
}

Bun.serve({
  hostname: HOST,
  port: PORT,
  idleTimeout: 120,
  async fetch(request) {
    const url = new URL(request.url);
    console.log('BFF request', { method: request.method, path: url.pathname });
    const target = new URL(`${url.pathname}${url.search}`, UPSTREAM).toString();
    if (!['GET', 'POST', 'DELETE'].includes(request.method)) {
      return new Response('Method not allowed', { status: 405 });
    }
    try {
      if (request.method === 'GET' && url.pathname === '/api/content') {
        const path = url.searchParams.get('path');
        if (!path || !path.startsWith('/')) {
          return Response.json({ message: 'path 无效' }, { status: 400 });
        }
        try {
          return await contentResponse(request, path);
        } catch (error) {
          console.error('BFF content error', { path, error: String(error) });
          return Response.json({ message: '内容解析失败' }, { status: 400 });
        }
      }
      if (request.method === 'GET' && CACHEABLE_GET.test(url.pathname)) {
        if (!inFlight.has(target)) {
          inFlight.set(target, publicRequest(request, target, url.pathname).finally(() => inFlight.delete(target)));
        }
        const { entry, cacheState } = await inFlight.get(target);
        return makeResponse(entry, cacheState);
      }
      const result = await directUpstream(request, target);
      const body = await result.arrayBuffer();
      return makeResponse({
        body,
        status: result.status,
        contentType: result.headers.get('content-type'),
        cookies: cookiesFrom(result.headers),
      }, 'bypass');
    } catch (error) {
      console.error('BFF upstream error', { path: url.pathname, error: String(error) });
      return Response.json({ message: '上游暂时不可用，请稍后重试' }, { status: 502 });
    }
  },
});

console.log(`MissAV BFF listening on http://${HOST}:${PORT}`);
console.log(`FlareSolverr recovery: ${FLARESOLVERR_URL ? 'enabled' : 'disabled'}`);
