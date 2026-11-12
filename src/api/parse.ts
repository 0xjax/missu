import { parse, type HTMLElement } from 'node-html-parser';

import { formatTime } from '../utils/format-time';

export const SOURCE_BASE_URL = 'https://missav.ai';

export type VideoSummary = {
  id: string;
  title: string;
  pageUrl: string;
  thumbnailUrl?: string;
  duration?: string;
  badges?: string[];
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

export type VideoFilter =
  | 'individual'
  | 'multiple'
  | 'chinese-subtitle'
  | 'jav'
  | 'asiaav'
  | 'uncensored-leak'
  | 'uncensored';
export type VideoSort = 'released_at' | 'published_at' | 'saved' | 'today_views' | 'weekly_views' | 'monthly_views' | 'views';
export type VideoQuery = {
  page?: number;
  filters?: VideoFilter;
  sort?: VideoSort;
};

export type DetailInfoValue = {
  text: string;
  path?: string;
};

export type DetailInfoRow = {
  label: string;
  values: DetailInfoValue[];
};

export type VideoDetail = VideoSummary & {
  description?: string;
  releaseDate?: string;
  actors: string[];
  playbackUrl?: string;
  internalId?: string;
  saved: boolean;
  info: DetailInfoRow[];
};

function absoluteUrl(value: string | undefined): string | undefined {
  if (!value || value.startsWith('data:')) return undefined;
  return new URL(value, SOURCE_BASE_URL).toString();
}

const CONTENT_LANGUAGE_SEGMENTS = new Set(['cn', 'en', 'ja', 'ko', 'ms', 'th']);

/** 去掉站点的 /dmXXXX 前缀和语言段，得到稳定的内容路径（例如 /cn/playlists/1 → /playlists/1）。 */
function contentPath(pathname: string): string {
  const segments = pathname.split('/').filter(Boolean);
  if (segments[0] && /^dm\d+$/.test(segments[0])) segments.shift();
  if (segments[0] && CONTENT_LANGUAGE_SEGMENTS.has(segments[0])) segments.shift();
  return `/${segments.join('/')}`;
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
    const spans = thumbnail.querySelectorAll('span').map((span) => ({
      className: span.getAttribute('class') ?? '',
      text: span.text.trim(),
    }));
    const duration = spans.find((span) => /^\d+:\d{2}(?::\d{2})?$/.test(span.text))?.text;
    // NOTE: 封面左下角是变体角标（中文字幕 / 無碼影片），右下角是时长；用位置类区分，同一棵子树内取，不额外请求。
    const badges = spans
      .filter((span) => span.className.includes('bottom-1')
        && span.className.includes('left-1')
        && !span.className.includes('right-1'))
      .map((span) => span.text)
      .filter(Boolean);

    return [{ id, title, pageUrl, thumbnailUrl, duration, badges }];
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
  // NOTE: 标题文案随语言变化（繁/简/英/日…），只在没有「更多」链接的板块靠标题兜底。
  const sectionTitles: Record<string, string> = {
    新作上市: 'release',
    最近更新: 'new',
    無碼影片: 'fc2',
    无码影片: 'fc2',
    隨機: 'random',
    随机: 'random',
  };
  const sectionPaths = new Set(['new', 'release', 'fc2', 'random']);

  return root.querySelectorAll('h1,h2,h3').flatMap((heading) => {
    const title = heading.text.trim();
    // 「更多」链接在标题所在行，用路径末段识别板块，避免依赖各语言的标题文案。
    // 页脚也有指向 new/release 的链接（同一行多条），只认唯一命中的标题行。
    const row = heading.parentNode?.parentNode as HTMLElement | undefined;
    const links = (row?.querySelectorAll('a[href]') ?? []).flatMap((anchor) => {
      const href = anchor.getAttribute('href');
      if (!href) return [];
      const path = new URL(href, SOURCE_BASE_URL).pathname;
      const id = path.split('/').filter(Boolean).at(-1);
      return id && sectionPaths.has(id) ? [{ id, path }] : [];
    });
    const link = new Set(links.map((item) => item.id)).size === 1 ? links[0] : undefined;

    const id = sectionTitles[title] ?? link?.id;
    if (!id) return [];
    const section = heading.parentNode?.parentNode?.parentNode as HTMLElement | undefined;
    if (!section) return [];
    const videos = parseVideoCards(section);
    if (videos.length === 0) return [];
    return [{ id, title, videos, morePath: link?.path }];
  });
}

export function parseDirectory(html: string, kind: 'genres' | 'actresses' | 'makers', page = 1): DirectoryList {
  const root = parse(html);
  const marker = `/${kind}/`;
  const byPath = new Map<string, DirectoryItem>();

  for (const anchor of root.querySelectorAll('a.text-nord13[href]')) {
    const href = anchor.getAttribute('href');
    if (!href) continue;
    const path = new URL(href, SOURCE_BASE_URL).pathname;
    if (!path.includes(marker) || path.endsWith('/ranking')) continue;
    const raw = anchor.text.trim().replace(/\s+/g, ' ');
    if (!raw) continue;

    // NOTE: 计数文案随语言变化（條影片 / 条影片 / 本の動画 / videos），名字在计数前，不能用固定繁体词切。
    const cut = raw.search(/(?:\s+\d+\s+(?:條|条)?影片|\s+\d+\s+本の動画|\s+\d+\s+videos?\b|\s+第\s*\d+\s*名|\s+(?:No\.?|#)\s*\d+)/i);
    const name = (cut > 0 ? raw.slice(0, cut) : raw).trim();
    if (!name) continue;
    const parentText = (anchor.parentNode as HTMLElement).text.trim().replace(/\s+/g, ' ');
    const subtitle = raw.slice(name.length).trim() || parentText.replace(raw, '').trim() || undefined;
    const card = anchor.parentNode?.parentNode as HTMLElement | undefined;
    const image = card?.querySelector('img');
    const imageUrl = absoluteUrl(image?.getAttribute('data-src') ?? image?.getAttribute('src'));

    // NOTE: 无头像的条目站点用“首字占位块”当头像，会先命中一个单字链接；同路径取名字最长的那条。
    const existing = byPath.get(path);
    if (!existing || name.length > existing.name.length) {
      byPath.set(path, { id: path, path, name, subtitle, imageUrl });
    }
  }

  const items = [...byPath.values()];
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
  return formatTime(seconds);
}

/**
 * 详情页信息块：每行首个 span 是标签，其余子元素是值（time / span / a）。
 * 标签文案随内容语言变化，不硬编码；链接带 /dmNNN 与语言前缀，用 contentPath 归一化后可直接交给 /latest。
 */
function parseDetailInfo(root: HTMLElement): DetailInfoRow[] {
  return root.querySelectorAll('div.text-secondary').flatMap((row) => {
    const [labelNode, ...valueNodes] = row.children;
    const label = labelNode?.tagName === 'SPAN' ? labelNode.text.trim() : undefined;
    if (!label) return [];

    const values = valueNodes.flatMap((node): DetailInfoValue[] => {
      const text = node.text.trim();
      if (!text) return [];
      const href = node.getAttribute('href');
      if (!href || !/^(https?:\/\/|\/)/i.test(href)) return [{ text }];
      return [{ text, path: contentPath(new URL(href, SOURCE_BASE_URL).pathname) }];
    });

    return values.length > 0 ? [{ label, values }] : [];
  });
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
    info: parseDetailInfo(root),
  };
}

export type PlaylistSummary = {
  id: string;
  path: string;
  name: string;
  visibility?: string;
  owner?: string;
  updatedAt?: string;
};

export type PlaylistDetail = {
  name: string;
  videos: VideoSummary[];
};

/** 源站所有页面都内嵌登录弹窗（`id="login_email"`），不能据此判断；已登录页面才渲染 `/logout` 链接。 */
export function isLoginPage(html: string): boolean {
  return !/href="[^"]*\/logout"/i.test(html);
}

export type PlaylistCreateForm = {
  action: string;
  fields: Record<string, string>;
  nameField: string;
};

/**
 * 新建片单是网页表单（`POST /cn/playlists/create/{dvdId}`），不是 JSON 接口。
 * 字段名和 CSRF `_token` 都从表单里读，站点改字段时不用跟着改代码。
 */
export function parsePlaylistCreateForm(html: string): PlaylistCreateForm | undefined {
  const root = parse(html);
  const form = root.querySelectorAll('form').find((element) => element.querySelector('input[name]'));
  if (!form) return undefined;

  const inputs = form.querySelectorAll('input');
  const nameField = inputs
    .find((input) => {
      const type = input.getAttribute('type') ?? 'text';
      return (type === 'text' || type === 'search') && Boolean(input.getAttribute('name'));
    })
    ?.getAttribute('name');
  if (!nameField) return undefined;

  const fields: Record<string, string> = {};
  for (const input of inputs) {
    const name = input.getAttribute('name');
    if (!name) continue;
    const type = input.getAttribute('type') ?? 'text';
    if (type === 'submit' || type === 'button' || type === 'file') continue;
    if (type === 'checkbox' || type === 'radio') {
      if (input.getAttribute('checked') !== undefined) fields[name] = input.getAttribute('value') ?? 'on';
      continue;
    }
    fields[name] = input.getAttribute('value') ?? '';
  }

  return { action: form.getAttribute('action') ?? '', fields, nameField };
}

export function parsePlaylists(html: string): PlaylistSummary[] {
  const root = parse(html);
  const seen = new Set<string>();

  return root.querySelectorAll('a[href*="/playlists/"]').flatMap((anchor) => {
    const href = anchor.getAttribute('href');
    if (!href) return [];
    // NOTE: 渲染后的链接带语言前缀（/cn/playlists/xxx），先归一化再匹配。
    const path = contentPath(new URL(href, SOURCE_BASE_URL).pathname);
    if (!/^\/playlists\/[^/]+$/.test(path) || path === '/playlists/create') return [];
    if (seen.has(path)) return [];
    seen.add(path);

    const lines = anchor.text.split('\n').map((line) => line.trim()).filter(Boolean);
    const name = lines[0];
    if (!name) return [];
    const visibility = lines.find((line) => /^(私人|公開|公开|Private|Public)$/i.test(line));
    const updatedLine = lines.find((line) => /更新|Updated/i.test(line));
    const updatedAt = updatedLine?.match(/\d{4}-\d{2}-\d{2}/)?.[0];
    const owner = lines.find((line) => line !== name && line !== visibility && !line.includes('更新'));

    return [{
      id: path.split('/').filter(Boolean).at(-1)!,
      path,
      name,
      visibility,
      owner,
      updatedAt,
    }];
  });
}

export function parsePlaylistDetail(html: string): PlaylistDetail {
  const root = parse(html);
  return {
    name: (root.querySelector('h1')?.text.trim() ?? '').replace(/^(?:片單|片单|Playlist)[:：]\s*/i, '').trim(),
    videos: parseVideoCards(root),
  };
}
