import { parse, type HTMLElement } from 'node-html-parser';

const SOURCE_BASE_URL = 'https://missav.ai';
const CONTENT_BASE_URL = process.env.EXPO_PUBLIC_BFF_URL || SOURCE_BASE_URL;
let xsrfToken: string | undefined;

export type VideoSummary = {
  id: string;
  title: string;
  pageUrl: string;
  thumbnailUrl?: string;
  duration?: string;
};

export type VideoList = {
  videos: VideoSummary[];
  nextPage: number | null;
};

export type HomeSection = {
  id: string;
  title: string;
  videos: VideoSummary[];
  morePath?: string;
};

export type DirectoryItem = {
  id: string;
  path: string;
  name: string;
  subtitle?: string;
  imageUrl?: string;
};

export type DirectoryList = {
  items: DirectoryItem[];
  nextPage: number | null;
};

export type VideoFilter = 'individual' | 'multiple' | 'chinese-subtitle';
export type VideoSort = 'released_at' | 'published_at' | 'saved' | 'today_views' | 'weekly_views' | 'monthly_views' | 'views';
export type VideoQuery = {
  page?: number;
  filters?: VideoFilter;
  sort?: VideoSort;
};

export type AccountUser = {
  email: string;
  username?: string;
};

export type VideoDetail = VideoSummary & {
  description?: string;
  releaseDate?: string;
  actors: string[];
  playbackUrl?: string;
  internalId?: string;
  saved: boolean;
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

async function fetchHtml(path: string, baseUrl = CONTENT_BASE_URL, signal?: AbortSignal): Promise<string> {
  const response = await fetch(new URL(path, baseUrl), {
    headers: { Accept: 'text/html,application/xhtml+xml' },
    signal,
  });
  captureXsrfToken(response);
  if (!response.ok) throw new HttpError(response.status);
  return response.text();
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

async function fetchJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(new URL(path, SOURCE_BASE_URL), {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : {}),
      ...init.headers,
    },
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
}

function absoluteUrl(value: string | undefined): string | undefined {
  if (!value || value.startsWith('data:')) return undefined;
  return new URL(value, SOURCE_BASE_URL).toString();
}

function videoId(value: string): string | undefined {
  return new URL(value, SOURCE_BASE_URL).pathname.split('/').filter(Boolean).at(-1);
}

function parseVideoCards(root: HTMLElement): VideoSummary[] {
  return root.querySelectorAll('.thumbnail.group').flatMap((thumbnail) => {
    const href = thumbnail.querySelector('a[href]')?.getAttribute('href');
    const pageUrl = absoluteUrl(href);
    const id = pageUrl ? videoId(pageUrl) : undefined;
    if (!pageUrl || !id) return [];

    const card = thumbnail.parentNode as HTMLElement;
    const title = card.querySelector('a.text-secondary')?.text.trim() || id;
    const image = thumbnail.querySelector('img');
    const thumbnailUrl = absoluteUrl(image?.getAttribute('data-src') ?? image?.getAttribute('src'));
    const duration = thumbnail.querySelectorAll('span')
      .map((span) => span.text.trim())
      .find((text) => /^\d+:\d{2}(?::\d{2})?$/.test(text));

    return [{ id, title, pageUrl, thumbnailUrl, duration }];
  });
}

export function parseVideoList(html: string, page = 1): VideoList {
  const root = parse(html);
  const hasNextPage = root.querySelectorAll('a[href]').some((anchor) => {
    const href = anchor.getAttribute('href');
    return href ? new URL(href, SOURCE_BASE_URL).searchParams.get('page') === String(page + 1) : false;
  });
  return { videos: parseVideoCards(root), nextPage: hasNextPage ? page + 1 : null };
}

export function parseHome(html: string): HomeSection[] {
  const root = parse(html);
  const sectionIds: Record<string, string> = {
    新作上市: 'release',
    最近更新: 'new',
    無碼影片: 'fc2',
    隨機: 'random',
  };

  return root.querySelectorAll('h1,h2,h3').flatMap((heading) => {
    const title = heading.text.trim();
    const id = sectionIds[title];
    if (!id) return [];
    const section = heading.parentNode?.parentNode?.parentNode as HTMLElement | undefined;
    if (!section) return [];
    const videos = parseVideoCards(section);
    if (videos.length === 0) return [];
    const moreHref = section.querySelectorAll('a').find((anchor) => anchor.text.trim() === '更多')?.getAttribute('href');
    return [{
      id,
      title,
      videos,
      morePath: moreHref ? new URL(moreHref, SOURCE_BASE_URL).pathname : undefined,
    }];
  });
}

export function parseDirectory(html: string, kind: 'genres' | 'actresses' | 'makers', page = 1): DirectoryList {
  const root = parse(html);
  const marker = `/${kind}/`;
  const seen = new Set<string>();
  const items = root.querySelectorAll('a.text-nord13[href]').flatMap((anchor) => {
    const href = anchor.getAttribute('href');
    if (!href) return [];
    const path = new URL(href, SOURCE_BASE_URL).pathname;
    if (!path.includes(marker) || path.endsWith('/ranking') || seen.has(path)) return [];
    const raw = anchor.text.trim().replace(/\s+/g, ' ');
    if (!raw) return [];
    seen.add(path);

    const name = raw.split(/\s+(?:\d+\s+條影片|第\s+\d+\s+名)/)[0].trim();
    const parentText = (anchor.parentNode as HTMLElement).text.trim().replace(/\s+/g, ' ');
    const subtitle = raw.slice(name.length).trim() || parentText.replace(raw, '').trim() || undefined;
    const card = anchor.parentNode?.parentNode as HTMLElement | undefined;
    const image = card?.querySelector('img');
    const imageUrl = absoluteUrl(image?.getAttribute('data-src') ?? image?.getAttribute('src'));
    return [{ id: path, path, name, subtitle, imageUrl }];
  });
  const hasNextPage = root.querySelectorAll('a[href]').some((anchor) => {
    const href = anchor.getAttribute('href');
    return href ? new URL(href, SOURCE_BASE_URL).searchParams.get('page') === String(page + 1) : false;
  });
  return { items, nextPage: hasNextPage ? page + 1 : null };
}

function decodePackedString(value: string): string {
  let output = '';
  for (let index = 0; index < value.length; index += 1) {
    if (value[index] !== '\\') {
      output += value[index];
      continue;
    }
    const escaped = value[++index];
    if (escaped === undefined) break;
    if (escaped === 'x') {
      const hex = value.slice(index + 1, index + 3);
      output += String.fromCharCode(Number.parseInt(hex, 16));
      index += 2;
    } else if (escaped === 'u') {
      const hex = value.slice(index + 1, index + 5);
      output += String.fromCharCode(Number.parseInt(hex, 16));
      index += 4;
    } else {
      output += ({ n: '\n', r: '\r', t: '\t', b: '\b', f: '\f', v: '\v' } as Record<string, string>)[escaped] ?? escaped;
    }
  }
  return output;
}

function encodeBase(value: number, radix: number): string {
  const digits = '0123456789abcdefghijklmnopqrstuvwxyz';
  let output = '';
  do {
    output = digits[value % radix] + output;
    value = Math.floor(value / radix);
  } while (value > 0);
  return output;
}

function unpackScript(payload: string, radix: number, count: number, keywords: string): string {
  const dictionary = new Map<string, string>();
  const words = decodePackedString(keywords).split('|');
  for (let index = 0; index < count; index += 1) {
    const key = encodeBase(index, radix);
    dictionary.set(key, words[index] || key);
  }
  return decodePackedString(payload).replace(/\b\w+\b/g, (word) => dictionary.get(word) ?? word);
}

export function extractPlaybackUrl(html: string): string | undefined {
  const scripts = parse(html).querySelectorAll('script');
  const packedPattern = /eval\(function\(p,a,c,k,e,d\)[\s\S]*?\}\('((?:\\.|[^'\\])*)',(\d+),(\d+),'((?:\\.|[^'\\])*)'\.split\('\|'\)/g;

  for (const script of scripts) {
    for (const match of script.text.matchAll(packedPattern)) {
      const unpacked = unpackScript(match[1], Number(match[2]), Number(match[3]), match[4]);
      const urls = unpacked.match(/https?:\/\/[^\s"'<>\\]+/g) ?? [];
      const playbackUrl = urls.find((value) => {
        try {
          return new URL(value).pathname.endsWith('.m3u8');
        } catch {
          return false;
        }
      });
      if (playbackUrl) return playbackUrl;
    }
  }
  return undefined;
}

function meta(root: HTMLElement, property: string, name = 'property'): string | undefined {
  return root.querySelector(`meta[${name}="${property}"]`)?.getAttribute('content');
}

function formatDuration(seconds: number): string | undefined {
  if (!Number.isFinite(seconds) || seconds < 0) return undefined;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = Math.floor(seconds % 60);
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
    : `${minutes}:${String(remainder).padStart(2, '0')}`;
}

export function parseVideoDetail(html: string, id: string): VideoDetail {
  const root = parse(html);
  const title = meta(root, 'og:title') ?? root.querySelector('h1')?.text.trim() ?? id;
  const durationSeconds = Number(meta(root, 'og:video:duration'));
  return {
    id,
    title,
    pageUrl: `${SOURCE_BASE_URL}/${encodeURIComponent(id)}`,
    thumbnailUrl: absoluteUrl(meta(root, 'og:image')),
    duration: formatDuration(durationSeconds),
    description: meta(root, 'og:description') ?? meta(root, 'description', 'name'),
    releaseDate: meta(root, 'og:video:release_date'),
    actors: root.querySelectorAll('meta[property="og:video:actor"]')
      .map((element) => element.getAttribute('content')?.trim())
      .filter((actor): actor is string => Boolean(actor)),
    playbackUrl: extractPlaybackUrl(html),
    internalId: html.match(/\/api\/items\/([a-z0-9]+)\/view/i)?.[1],
    saved: false,
  };
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

export function getLatestVideos(options: VideoQuery = {}, signal?: AbortSignal): Promise<VideoList> {
  return getVideos('/new', options, signal);
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
