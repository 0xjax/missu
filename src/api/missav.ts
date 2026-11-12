import {
  isLoginPage,
  parseDirectory,
  parseHome,
  parsePlaylistDetail,
  parsePlaylists,
  parseVideoDetail,
  parseVideoList,
  SOURCE_BASE_URL,
  type DirectoryList,
  type HomeSection,
  type PlaylistDetail,
  type PlaylistSummary,
  type VideoDetail,
  type VideoList,
  type VideoQuery,
} from './parse';
import { withTimeout } from './with-timeout';

export type {
  DirectoryItem,
  DirectoryList,
  HomeSection,
  PlaylistDetail,
  PlaylistSummary,
  VideoDetail,
  VideoFilter,
  VideoList,
  VideoQuery,
  VideoSort,
  VideoSummary,
} from './parse';

let xsrfToken: string | undefined;

export type ContentLanguage = 'tw' | 'cn' | 'en' | 'ja' | 'ko' | 'ms' | 'th';

// NOTE: 站点把语言偏好放在路径段（/cn/new、/en/search/x），无前缀即繁體中文。
// 解析出的链接还会混入站点自己的 /dmXXXX 前缀，这里统一剥掉再按当前语言重建，调用点无需关心。
const LANGUAGE_SEGMENTS: readonly ContentLanguage[] = ['cn', 'en', 'ja', 'ko', 'ms', 'th'];
let contentLanguage: ContentLanguage = 'cn';

export function setContentLanguage(language: ContentLanguage): void {
  contentLanguage = language;
}

function localizedPath(path: string): string {
  const [pathname, search] = path.split('?');
  const segments = pathname.split('/').filter(Boolean);
  if (segments[0] && /^dm\d+$/.test(segments[0])) segments.shift();
  if (segments[0] && (LANGUAGE_SEGMENTS as readonly string[]).includes(segments[0])) segments.shift();
  const clean = `/${segments.join('/')}`;
  const suffix = search ? `?${search}` : '';
  if (contentLanguage === 'tw') return `${clean}${suffix}`;
  return clean === '/' ? `/${contentLanguage}${suffix}` : `/${contentLanguage}${clean}${suffix}`;
}

export type AccountUser = {
  email: string;
  username?: string;
};

export class HttpError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`请求失败（HTTP ${status}）`);
    this.name = 'HttpError';
    this.status = status;
  }
}

export class SessionExpiredError extends Error {
  constructor() {
    super('登录已失效，请重新登录');
    this.name = 'SessionExpiredError';
  }
}

/** 源站触发了 Cloudflare 人机验证，需要 WebView 处理。 */
export class ChallengeError extends Error {
  readonly path: string;

  constructor(path: string) {
    super('需要完成人机验证');
    this.name = 'ChallengeError';
    this.path = path;
  }
}

// NOTE: 验证器由 UI 层（ChallengeProvider）注册；验证期间请求原地等待，通过后自动重试一次。
type ChallengeVerifier = (path: string) => Promise<boolean>;
let challengeVerifier: ChallengeVerifier | undefined;
// NOTE: cf_clearance 绑定 IP + User-Agent，验证成功后用 WebView 的 UA 发请求，否则验证无效。
let browserUserAgent: string | undefined;

export function setChallengeVerifier(verifier: ChallengeVerifier | undefined): void {
  challengeVerifier = verifier;
}

export function setBrowserUserAgent(userAgent: string): void {
  browserUserAgent = userAgent;
}

export function localizedUrl(path: string): string {
  return new URL(localizedPath(path), SOURCE_BASE_URL).toString();
}

function requestHeaders(extra: Record<string, string> = {}): Record<string, string> {
  return {
    ...(browserUserAgent ? { 'User-Agent': browserUserAgent } : {}),
    ...extra,
  };
}

function isChallengeResponse(response: Response, body?: string): boolean {
  if (response.status === 403) return true;
  if (response.headers.get('cf-mitigated') === 'challenge') return true;
  return body ? /<title>\s*Just a moment\.\.\.\s*<\/title>/i.test(body) : false;
}

async function withChallengeRetry<T>(path: string, load: () => Promise<T>, signal?: AbortSignal | null): Promise<T> {
  try {
    return await load();
  } catch (error) {
    if (!(error instanceof ChallengeError) || !challengeVerifier || signal?.aborted) throw error;
    const verified = await challengeVerifier(path);
    if (!verified) throw error;
    return load();
  }
}

function captureXsrfToken(response: Response): void {
  const cookie = response.headers.get('set-cookie');
  const value = cookie?.match(/(?:^|,\s*)XSRF-TOKEN=([^;]+)/i)?.[1];
  if (!value) return;
  try {
    xsrfToken = decodeURIComponent(value);
  } catch {
    xsrfToken = value;
  }
}

async function fetchHtml(path: string, baseUrl = SOURCE_BASE_URL, signal?: AbortSignal): Promise<string> {
  const load = () => withTimeout(async (requestSignal) => {
    const response = await fetch(new URL(localizedPath(path), baseUrl), {
      headers: requestHeaders({ Accept: 'text/html,application/xhtml+xml' }),
      signal: requestSignal,
    });
    captureXsrfToken(response);
    if (isChallengeResponse(response)) throw new ChallengeError(path);
    if (!response.ok) throw new HttpError(response.status);
    const html = await response.text();
    if (isChallengeResponse(response, html)) throw new ChallengeError(path);
    return html;
  }, signal);

  return withChallengeRetry(path, load, signal);
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

async function fetchJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const { signal, ...requestInit } = init;
  const load = () => withTimeout(async (requestSignal) => {
    const response = await fetch(new URL(path, SOURCE_BASE_URL), {
      ...requestInit,
      headers: requestHeaders({
        Accept: 'application/json',
        ...(requestInit.body ? { 'Content-Type': 'application/json' } : {}),
        ...(xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : {}),
        ...(requestInit.headers as Record<string, string> | undefined),
      }),
      signal: requestSignal,
    });
    captureXsrfToken(response);
    if (isChallengeResponse(response)) throw new ChallengeError(path);
    const text = await response.text();
    if (isChallengeResponse(response, text)) throw new ChallengeError(path);
    let data: { message?: string; errors?: Record<string, string[]> } | null = null;
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
    if (!response.ok) {
      const firstError = data?.errors ? Object.values(data.errors)[0]?.[0] : undefined;
      throw new Error(firstError ?? data?.message ?? `请求失败（HTTP ${response.status}）`);
    }
    if (data === null) {
      const contentType = response.headers.get('content-type') ?? '';
      throw new Error(contentType.includes('text/html') ? '源站暂时拦截了请求，请稍后再试' : '请求返回异常，请稍后重试');
    }
    return data as T;
  }, signal);

  return withChallengeRetry(path, load, signal);
}

function queryPath(path: string, { page = 1, filters, sort }: VideoQuery): string {
  const params = new URLSearchParams();
  if (filters) params.set('filters', filters);
  if (sort) params.set('sort', sort);
  if (page > 1) params.set('page', String(page));
  const query = params.toString();
  return query ? `${path}?${query}` : path;
}

export async function getHome(signal?: AbortSignal): Promise<HomeSection[]> {
  return parseHome(await fetchHtml('/', undefined, signal));
}

export async function getVideos(path: string, options: VideoQuery = {}, signal?: AbortSignal): Promise<VideoList> {
  const page = options.page ?? 1;
  return parseVideoList(await fetchHtml(queryPath(path, options), undefined, signal), page);
}

export async function getDirectory(path: string, page = 1, signal?: AbortSignal): Promise<DirectoryList> {
  const kind = path.includes('/actresses') ? 'actresses' : path.includes('/makers') ? 'makers' : 'genres';
  return parseDirectory(await fetchHtml(queryPath(path, { page }), undefined, signal), kind, page);
}

export type ActressQuery = {
  page?: number;
  sort?: 'videos' | 'debut';
  filters?: Partial<Record<'height' | 'cup' | 'age' | 'debut', string>>;
};

/** 女优一览支持身高/罩杯/年龄/出道年份筛选与两种排序，参数与源站一致。 */
export async function getActresses(query: ActressQuery = {}, signal?: AbortSignal): Promise<DirectoryList> {
  const page = query.page ?? 1;
  const params = new URLSearchParams();
  if (query.sort) params.set('sort', query.sort);
  for (const [key, value] of Object.entries(query.filters ?? {})) {
    if (value) params.set(key, value);
  }
  if (page > 1) params.set('page', String(page));
  const search = params.toString();
  const html = await fetchHtml(search ? `/actresses?${search}` : '/actresses', undefined, signal);
  return parseDirectory(html, 'actresses', page);
}

export async function getSavedVideos(options: VideoQuery = {}, signal?: AbortSignal): Promise<VideoList> {
  const page = options.page ?? 1;
  const html = await fetchHtml(queryPath('/saved', options), SOURCE_BASE_URL, signal);
  if (isLoginPage(html)) throw new SessionExpiredError();
  return parseVideoList(html, page);
}

export async function getPlaylists(signal?: AbortSignal): Promise<PlaylistSummary[]> {
  const html = await fetchHtml('/playlists', undefined, signal);
  if (isLoginPage(html)) throw new SessionExpiredError();
  return parsePlaylists(html);
}

export async function getPlaylistDetail(id: string, signal?: AbortSignal): Promise<PlaylistDetail> {
  const html = await fetchHtml(`/playlists/${encodeURIComponent(id)}`, undefined, signal);
  if (isLoginPage(html)) throw new SessionExpiredError();
  return parsePlaylistDetail(html);
}

export async function searchVideos(keyword: string, options: VideoQuery = {}, signal?: AbortSignal): Promise<VideoList> {
  const page = options.page ?? 1;
  return parseVideoList(await fetchHtml(queryPath(`/search/${encodeURIComponent(keyword)}`, options), undefined, signal), page);
}

export async function getVideoDetail(id: string, signal?: AbortSignal): Promise<VideoDetail> {
  const detail = parseVideoDetail(await fetchHtml(`/${encodeURIComponent(id)}`, undefined, signal), id);
  if (!detail.internalId) return detail;
  try {
    const state = await fetchJson<{ saved: boolean }>(`/api/items/${detail.internalId}/view`, { signal });
    return { ...detail, saved: state.saved };
  } catch (error) {
    if (isAbortError(error)) throw error;
    return detail;
  }
}

export function setVideoSaved(internalId: string, saved: boolean): Promise<unknown> {
  return fetchJson(`/api/items/${internalId}/save`, { method: saved ? 'POST' : 'DELETE' });
}

export async function login(email: string, password: string): Promise<AccountUser> {
  const result = await fetchJson<{ user?: { name?: string } }>('/api/login', {
    method: 'POST',
    body: JSON.stringify({ email, password, remember: true }),
  });
  // NOTE: 源站只返回 name，客户端用本次登录邮箱补全 AccountUser。
  return { email, username: result?.user?.name };
}

/** 启动时校验服务端会话是否还有效；网络/风控失败时按“仍然有效”处理，避免误登出。 */
export async function checkSession(signal?: AbortSignal): Promise<boolean> {
  try {
    const html = await fetchHtml('/playlists', undefined, signal);
    return !isLoginPage(html);
  } catch {
    return true;
  }
}

export async function logout(): Promise<void> {
  const response = await fetch(new URL('/logout', SOURCE_BASE_URL), {
    headers: requestHeaders(xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : {}),
  });
  captureXsrfToken(response);
  if (!response.ok) throw new HttpError(response.status);
}
