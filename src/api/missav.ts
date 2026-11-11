import {
  parseDirectory,
  parseHome,
  parseVideoDetail,
  parseVideoList,
  SOURCE_BASE_URL,
  type DirectoryList,
  type HomeSection,
  type VideoDetail,
  type VideoList,
  type VideoQuery,
} from './parse';
import { withTimeout } from './with-timeout';

export type {
  DirectoryItem,
  DirectoryList,
  HomeSection,
  VideoDetail,
  VideoFilter,
  VideoList,
  VideoQuery,
  VideoSort,
  VideoSummary,
} from './parse';

let xsrfToken: string | undefined;

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
  return withTimeout(async (requestSignal) => {
    const response = await fetch(new URL(path, baseUrl), {
      headers: { Accept: 'text/html,application/xhtml+xml' },
      signal: requestSignal,
    });
    captureXsrfToken(response);
    if (!response.ok) throw new HttpError(response.status);
    return response.text();
  }, signal);
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

async function fetchJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const { signal, ...requestInit } = init;
  return withTimeout(async (requestSignal) => {
    const response = await fetch(new URL(path, SOURCE_BASE_URL), {
      ...requestInit,
      headers: {
        Accept: 'application/json',
        ...(requestInit.body ? { 'Content-Type': 'application/json' } : {}),
        ...(xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : {}),
        ...requestInit.headers,
      },
      signal: requestSignal,
    });
    captureXsrfToken(response);
    const data = await response.json().catch(() => null) as {
      message?: string;
      errors?: Record<string, string[]>;
    } | null;
    if (!response.ok) {
      const firstError = data?.errors ? Object.values(data.errors)[0]?.[0] : undefined;
      throw new Error(firstError ?? data?.message ?? `请求失败（HTTP ${response.status}）`);
    }
    return data as T;
  }, signal);
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

export async function getSavedVideos(options: VideoQuery = {}, signal?: AbortSignal): Promise<VideoList> {
  const page = options.page ?? 1;
  return parseVideoList(await fetchHtml(queryPath('/saved', options), SOURCE_BASE_URL, signal), page);
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
  const result = await fetchJson<{ user: { name?: string } }>('/api/login', {
    method: 'POST',
    body: JSON.stringify({ email, password, remember: true }),
  });
  // NOTE: 源站只返回 name，客户端用本次登录邮箱补全 AccountUser。
  return { email, username: result.user?.name };
}

export async function logout(): Promise<void> {
  const response = await fetch(new URL('/logout', SOURCE_BASE_URL), {
    headers: xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : undefined,
  });
  captureXsrfToken(response);
  if (!response.ok) throw new HttpError(response.status);
}
