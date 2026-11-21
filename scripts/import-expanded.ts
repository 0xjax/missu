import OpenCC from "opencc-js";
import { mkdir } from "node:fs/promises";

const root = new URL("..", import.meta.url).pathname.replace(/^\/(?:([A-Za-z]:))/, "$1");
const local = `${root}/.local/literature`;
const input = `${local}/imported-base.json`;
const output = `${local}/imported.json`;
const manifestPath = `${root}/content/literature/manifest.json`;
const daizhigeRepository = "https://github.com/daizhige-org/daizhigev20";
const daizhigeRevision = "409cd37f76567ba5631a0a0557b18566b0f20054";
const convert = OpenCC.Converter({ from: "twp", to: "cn" });
const emptyText = { en: "", zh: "", "zh-Hant": "", ja: "" };
const emptyNotes = { en: [], zh: [], "zh-Hant": [], ja: [] };

type Chapter = { id: string; title: string; text: string };
type Work = {
  id: string;
  kind: string;
  title: string;
  author: string;
  text: string;
  source: string;
  prominence: number;
  theme: typeof emptyText;
  notes: typeof emptyNotes;
  translation: typeof emptyText;
  appreciation: typeof emptyText;
  canonical: boolean;
  chapters: Chapter[];
};

function cleanText(value: string): string {
  return convert(value)
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/<[^>]+>/g, "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .join("\n")
    .trim();
}

function identity(author: string, text: string): string {
  const plainAuthor = author.split("·").pop()?.replace(/[撰著\s]/g, "") ?? author;
  return `${convert(plainAuthor)}|${convert(text).replace(/[^\p{L}\p{N}]/gu, "")}`;
}

function makeWork(
  id: string,
  kind: string,
  title: string,
  author: string,
  text: string,
  source: string,
  chapters: Chapter[] = [],
): Work {
  return {
    id,
    kind,
    title,
    author,
    text,
    source,
    prominence: 2_000_000_000,
    theme: { ...emptyText },
    notes: { ...emptyNotes },
    translation: { ...emptyText },
    appreciation: { ...emptyText },
    canonical: kind === "BOOK",
    chapters,
  };
}

async function fetchCached(url: string, folder = "http"): Promise<string> {
  const directory = `${local}/${folder}`;
  await mkdir(directory, { recursive: true });
  const hash = new Bun.CryptoHasher("sha256").update(url).digest("hex");
  const target = `${directory}/${hash}.txt`;
  if (!await Bun.file(target).exists()) {
    let error: unknown;
    for (let attempt = 1; attempt <= 4; attempt++) {
      try {
        const response = await fetch(url);
        if (response.status === 429) {
          const retryAfter = Number(response.headers.get("retry-after") ?? 30);
          await Bun.sleep((Number.isFinite(retryAfter) ? retryAfter : 30) * 1000);
          continue;
        }
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        await Bun.write(target, await response.arrayBuffer());
        break;
      } catch (current) {
        error = current;
        await Bun.sleep(attempt * 1000);
      }
    }
    if (!await Bun.file(target).exists()) throw new Error(`Download failed: ${url}: ${error}`);
  }
  return Bun.file(target).text();
}

function splitChapters(text: string, book: string, pattern: RegExp): Chapter[] {
  const lines = text.split(/\r?\n/);
  const starts = lines.map((line, index) => pattern.test(line.trim()) ? index : -1)
    .filter((index) => index >= 0);
  return starts.map((start, index) => ({
    id: `${book}-${index + 1}`,
    title: cleanText(lines[start]).replace(/^[●○]/, ""),
    text: cleanText(lines.slice(start + 1, starts[index + 1] ?? lines.length).join("\n")),
  })).filter((chapter) => chapter.text.length > 0);
}

async function daizhigeChapters(
  path: string,
  book: string,
  pattern?: RegExp,
  inline?: RegExp,
  transform?: (text: string) => string,
): Promise<Chapter[]> {
  const url = `${daizhigeRepository.replace("github.com", "raw.githubusercontent.com")}/${daizhigeRevision}/${encodeURI(path.replace(/\.txt$/, ".md"))}`;
  const text = await fetchCached(url, "daizhige");
  const normalized = text
    .replace(/^\uFEFF?---\r?\n[\s\S]*?\r?\n---\r?\n/, "")
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/\\([.．])/g, "$1")
    .replace(/[ \t]+$/gm, "");
  const transformed = transform ? transform(normalized) : normalized;
  if (!pattern) return [{ id: `${book}-1`, title: "全文", text: cleanText(transformed) }];
  const prepared = inline ? transformed.replace(inline, "\n$&\n") : transformed;
  return splitChapters(prepared, book, pattern);
}

async function moziChapters(): Promise<Chapter[]> {
  const pattern = /^(?!卷).+第[一二三四五六七八九十百]+$/;
  const primary = await daizhigeChapters("子藏/诸子/墨子.txt", "mozi", pattern);
  const alternate = await daizhigeChapters(
    "道藏/正统道藏太清部/墨子.txt",
    "mozi-alternate",
    pattern,
    undefined,
    (text) => text.slice(text.indexOf("墨子卷之一")).split(/\r?\n/)
      .filter((line) => !/^\s*#\d+/.test(line) && !/^\s*墨子卷之.+竟\s*$/.test(line))
      .join("\n").replace(/#\d+/g, ""),
  );
  const additions = new Map(alternate.filter((chapter) => ["尚贤下第十", "鲁问第四十九"].includes(chapter.title)).map((chapter) => [chapter.title, chapter.text]));
  if (additions.size !== 2) throw new Error("墨子补充底本缺少预期篇章");
  const merged = primary.flatMap((chapter) => {
    const addition = chapter.title === "尚贤中第九" ? "尚贤下第十" : chapter.title === "公孟第四十八" ? "鲁问第四十九" : null;
    return addition ? [chapter, { id: "", title: addition, text: additions.get(addition)! }] : [chapter];
  });
  return merged.map((chapter, index) => ({ ...chapter, id: `mozi-${index + 1}` }));
}

function shijiText(markdown: string): string {
  return markdown.split("\n")
    .filter((line) => !line.startsWith("#") && line.trim() !== "---")
    .map((line) => line
      .replace(/〖.([^〗]*)〗/gu, "$1")
      .replace(/⟦.([^⟧]*)⟧/gu, "$1")
      .replace(/〘.([^〙]*)〙/gu, "$1")
      .replace(/^\s*-?\s*\[[\d.]+\]\s*/, "")
      .trim())
    .filter(Boolean)
    .join("\n");
}

async function shijiChapters(): Promise<Chapter[]> {
  const revision = "6b836e3fac1b900ccc6e9299fd6896c0e18ec132";
  const listing = await (await fetch(`https://api.github.com/repos/baojie/shiji-kb/contents/chapter_md?ref=${revision}`)).json() as any[];
  const files = listing.filter((file) => file.name.endsWith(".tagged.md"));
  if (files.length !== 130) throw new Error(`Unexpected Shiji chapter count: ${files.length}`);
  const chapters: Chapter[] = [];
  for (const file of files) {
    const url = `https://raw.githubusercontent.com/baojie/shiji-kb/${revision}/${encodeURI(file.path)}`;
    const markdown = await fetchCached(url, "shiji");
    chapters.push({
      id: `shiji-${chapters.length + 1}`,
      title: file.name.replace(/^\d+_/, "").replace(/\.tagged\.md$/, ""),
      text: shijiText(markdown),
    });
  }
  return chapters;
}

async function classicNovelChapters(title: string, book: string): Promise<Chapter[]> {
  const revision = "c5ce96aca36c6a3cb4da5f0ff7d2d5fa30c32aea";
  const url = `https://raw.githubusercontent.com/Jiasheng-Shi/Dream-of-the-Red-Chamber/${revision}/${encodeURI(title)}.txt`;
  const text = await fetchCached(url, "four-novels");
  return splitChapters(text, book, /^第[0-9一二三四五六七八九十百千万零〇]+回\s+/);
}

function translationAndNotes(sons: Record<string, any> | undefined) {
  const entry = Object.entries(sons ?? {}).find(([key]) => key.startsWith("译文及注释"));
  const raw = String(entry?.[1]?.content ?? "");
  const split = raw.split(/注释(?:&nbsp;|<br\s*\/?>)/i);
  const translation = cleanText(split[0].replace(/^译文(?:<br\s*\/?>)+/i, ""));
  const notes = split.length > 1 ? cleanText(split.slice(1).join("\n")) : "";
  return { translation, notes };
}

const expandedBookIds = new Set([
  "daodejing", "tongjian", "shiji", "sanguo", "shuihu", "xiyouji", "hongloumeng",
  "daxue", "zhongyong", "mengzi", "zhouyi", "shangshu", "liji", "chunqiu-zuozhuan",
  "xiaojing", "mozi", "zhuangzi", "xunzi", "hanfeizi", "sunzi", "lvshi-chunqiu",
  "huainanzi", "guoyu", "zhanguoce", "shanhaijing",
]);
const works = (await Bun.file(input).json() as Work[])
  .filter((work) => !expandedBookIds.has(work.id) && !work.id.startsWith("gushiwen-"));
const before = works.length;
const byText = new Map(works.map((work) => [identity(work.author, work.text), work]));
const knownCi = new Set(works.filter((work) => work.kind === "CI").map((work) => work.title.split("·")[0].trim()));
const knownQu = new Set(works.filter((work) => work.kind === "QU").flatMap((work) => work.title.split(/[·/]/).map((part) => part.trim())));
const prosePattern = /(记|序|赋|说|论|书|表|铭|诔|檄|祭文|列传|世家|本纪|墓志|奏折|策问|诏|诫|训|赞|颂|志|传)$/;
const gushiwen = await Bun.file(`${local}/gushiwen.json`).json() as any[];
let gushiwenAdded = 0;
let gushiwenDuplicates = 0;
let gushiwenEnriched = 0;
let nalanReplaced = 0;
for (const item of gushiwen) {
  const text = cleanText(String(item.content ?? ""));
  if (!text) continue;
  if (cleanText(String(item.author ?? "")) === "纳兰性德") {
    nalanReplaced++;
    continue;
  }
  const key = identity(String(item.author ?? "佚名"), text);
  const supplemental = translationAndNotes(item.sons);
  const appreciation = cleanText(String(item.sons?.赏析?.content ?? item.sons?.鉴赏?.content ?? ""));
  const existing = byText.get(key);
  if (existing) {
    let changed = false;
    if (!existing.translation.zh && supplemental.translation) {
      existing.translation.zh = supplemental.translation;
      changed = true;
    }
    if (!existing.notes.zh.length && supplemental.notes) {
      existing.notes.zh = [supplemental.notes];
      changed = true;
    }
    if (!existing.appreciation.zh && appreciation) {
      existing.appreciation.zh = appreciation;
      changed = true;
    }
    if (changed) gushiwenEnriched++;
    gushiwenDuplicates++;
    continue;
  }
  const title = cleanText(String(item.title ?? "无题")) || "无题";
  const dynasty = cleanText(String(item.dynasty ?? ""));
  const author = `${dynasty} · ${cleanText(String(item.author ?? "佚名")) || "佚名"}`;
  const tune = title.split("·")[0].trim();
  const isCi = knownCi.has(tune);
  const isQu = knownQu.has(tune);
  const kind = isQu && dynasty === "元代" ? "QU" : isCi ? "CI" : isQu ? "QU" : prosePattern.test(title) ? "PROSE" : "POEM";
  const work = makeWork(
    `gushiwen-${item.id}`,
    kind,
    title,
    author,
    text,
    "yht050511/gushiwen · 古诗文网",
  );
  work.prominence = 0;
  work.translation.zh = supplemental.translation;
  if (supplemental.notes) work.notes.zh = [supplemental.notes];
  work.appreciation.zh = appreciation;
  works.push(work);
  byText.set(key, work);
  gushiwenAdded++;
}

const books = [
  { id: "daodejing", title: "道德经", author: "春秋 · 老子", path: "道藏/正统道藏洞神部/本文类/道德经古本篇.txt", pattern: /^(?:右)?第[一二三四五六七八九十百]+章/, expected: 81 },
  { id: "tongjian", title: "资治通鉴", author: "北宋 · 司马光", path: "史藏/编年/资治通鉴.txt", pattern: /^●卷第[一二三四五六七八九十百]+/, expected: 294 },
  { id: "daxue", title: "大学", author: "先秦 · 曾子", path: "儒藏/四书/大学.txt", expected: 1 },
  { id: "zhongyong", title: "中庸", author: "战国 · 子思", path: "儒藏/四书/中庸.txt", expected: 1 },
  { id: "mengzi", title: "孟子", author: "战国 · 孟子及其弟子", path: "儒藏/四书/孟子.txt", pattern: /^卷[一二三四五六七八九十]+\s+.+$/, inline: /卷[一二三四五六七八九十]+\s+[^\n]+/g, expected: 14 },
  { id: "zhouyi", title: "周易", author: "先秦 · 佚名", path: "易藏/易经/周易.txt", pattern: /^(?:\d{2}[.．]\s*.+|系辞[上下]|说卦|序卦|杂卦)$/, expected: 69 },
  { id: "shangshu", title: "尚书", author: "先秦 · 佚名", path: "儒藏/尚书/尚书.txt", pattern: /^(?:虞|夏|商|周)书\s+.+第[一二三四五六七八九十百]+$/, inline: /(?:虞|夏|商|周)书\s+[^\n]+?第[一二三四五六七八九十百]+/g, expected: 58 },
  { id: "liji", title: "礼记", author: "西汉 · 戴圣 编", path: "儒藏/礼经/礼记.txt", pattern: /^《礼记.+》$/, expected: 47 },
  { id: "chunqiu-zuozhuan", title: "春秋左传", author: "春秋 · 左丘明", path: "儒藏/春秋/春秋左传.txt", pattern: /^(?:隐|桓|庄|闵|僖|文|宣|成|襄|昭|定|哀)公（.+）$/, inline: /(?:隐|桓|庄|闵|僖|文|宣|成|襄|昭|定|哀)公（[^\n]+）/g, expected: 12 },
  { id: "xiaojing", title: "孝经", author: "先秦 · 孔子及其后学", path: "儒藏/孝经/孝经.txt", pattern: /^○.+章第[一二三四五六七八九十百]+$/, inline: /○[^\n○]+?章第[一二三四五六七八九十百]+/g, expected: 18 },
  { id: "mozi", title: "墨子", author: "战国 · 墨子及其后学", path: "子藏/诸子/墨子.txt", pattern: /^(?!卷).+第[一二三四五六七八九十百]+$/, expected: 53 },
  { id: "zhuangzi", title: "庄子", author: "战国 · 庄子及其后学", path: "道藏/藏外/庄子.txt", pattern: /^卷[一二三四五六七八九十]+[上中下]?\s*第[一二三四五六七八九十百]+\s*.+$/, inline: /卷[一二三四五六七八九十]+[上中下]?\s*第[一二三四五六七八九十百]+\s*[^\n]+/g, expected: 33 },
  { id: "xunzi", title: "荀子", author: "战国 · 荀子", path: "子藏/诸子/荀子.txt", pattern: /^(?!.*篇第.*篇第).+篇第[一二三四五六七八九十百]+$/, expected: 32 },
  { id: "hanfeizi", title: "韩非子", author: "战国 · 韩非", path: "子藏/诸子/韩非子.txt", pattern: /^(?:初见秦|存韩|难言|爱臣|主道|有度|二柄|扬权|八奸|十过|孤愤|说难|和氏|奸劫杀臣|亡徽|三守|备内|南面|饰邪|解老|喻老|说林上|说林下|观行|安危|守道|用人|功名|大体|内储说上七术|内储说下六微|外储说左上|外储说左下|外储说右上|外储说右下|难一|难二|难三|难四|难势|问辩|问田|定法|说疑|诡使|六反|八说|八经|五蠹|显学|忠孝|人主|饬令|心度|制分)(?:第[一二三四五六七八九十百]+)?(?:〈全〉)?$/, expected: 55 },
  { id: "sunzi", title: "孙子兵法", author: "春秋 · 孙武", path: "子藏/兵家/孙子兵法.txt", pattern: /^.+篇第[一二三四五六七八九十百]+.*$/, expected: 13 },
  { id: "lvshi-chunqiu", title: "吕氏春秋", author: "战国 · 吕不韦 等", path: "子藏/类书/吕氏春秋.txt", pattern: /^《.+第[一二三四五六七八九十百]+》$/, expected: 26 },
  { id: "huainanzi", title: "淮南子", author: "西汉 · 刘安 等", path: "子藏/诸子/淮南子.txt", pattern: /^卷[一二三四五六七八九十百]+.+$/, expected: 21 },
  { id: "guoyu", title: "国语", author: "春秋 · 左丘明", path: "史藏/别史/国语.txt", pattern: /^卷[一二三四五六七八九十百]+\s+.+$/, inline: /卷[一二三四五六七八九十百]+\s+[^\n]+/g, expected: 21 },
  { id: "zhanguoce", title: "战国策", author: "西汉 · 刘向 编", path: "史藏/志存记录/战国策.txt", pattern: /^卷[一二三四五六七八九十百]+\s+.+$/, inline: /卷[一二三四五六七八九十百]+\s+[^\n]+/g, transform: (text: string) => text.slice(text.indexOf("卷一 东周", text.indexOf("卷一 东周") + 1)), expected: 33 },
  { id: "shanhaijing", title: "山海经", author: "先秦 · 佚名", path: "史藏/志存记录/山海经.txt", pattern: /^●.+经$/, expected: 18 },
];
for (const book of books) {
  const chapters = book.id === "mozi"
    ? await moziChapters()
    : await daizhigeChapters(book.path, book.id, book.pattern, book.inline, book.transform);
  if (chapters.length !== book.expected) throw new Error(`${book.title}: ${chapters.length} != ${book.expected}`);
  works.push(makeWork(book.id, "BOOK", book.title, book.author, "", "daizhige-org/daizhigev20 · 殆知阁", chapters));
}
const novels = [
  ["sanguo", "三国演义", "明 · 罗贯中", 120],
  ["shuihu", "水浒传", "明 · 施耐庵", 120],
  ["xiyouji", "西游记", "明 · 吴承恩", 100],
  ["hongloumeng", "红楼梦", "清 · 曹雪芹", 120],
] as const;
for (const [id, title, author, expected] of novels) {
  const chapters = await classicNovelChapters(title, id);
  if (chapters.length !== expected) throw new Error(`${title}: ${chapters.length} != ${expected}`);
  works.push(makeWork(id, "BOOK", title, author, "", "Jiasheng-Shi/Dream-of-the-Red-Chamber", chapters));
}
works.push(makeWork("shiji", "BOOK", "史记", "西汉 · 司马迁", "", "baojie/shiji-kb · GitHub 分篇", await shijiChapters()));

const uniqueWorks = [...new Map(works.map((work) => [work.id, work])).values()];
await Bun.write(output, `${JSON.stringify(uniqueWorks, null, 2)}\n`);
const manifest = await Bun.file(manifestPath).json();
manifest.databaseVersion = 5;
manifest.sources = manifest.sources.filter((source: any) => !["gushiwen", "daizhige", "four-novels", "shiji"].includes(source.id));
manifest.composition.classicCollections = manifest.composition.classicCollections.filter((book: any) => !expandedBookIds.has(book.id));
manifest.sources.push(
  { id: "gushiwen", repository: "https://github.com/yht050511/gushiwen", revision: "409df701b3f91a41c87e5979b092c8c3c42c2123", revisionDate: "2022-12-29T05:59:54Z", license: "MIT；含开放声明的译文、注释与赏析" },
  { id: "daizhige", repository: daizhigeRepository, revision: daizhigeRevision, revisionDate: "2026-10-28T23:07:36Z", license: "典籍原文公有领域", note: "官方维护 Markdown 版；提供道德经、资治通鉴及四书五经、诸子、史地典籍，剥离 frontmatter 后按篇卷拆分并校验章数" },
  { id: "four-novels", repository: "https://github.com/Jiasheng-Shi/Dream-of-the-Red-Chamber", revision: "c5ce96aca36c6a3cb4da5f0ff7d2d5fa30c32aea", revisionDate: "2019-03-22T06:50:04Z", license: "典籍原文公有领域", note: "四大名著完整分回文本；选用标题结构完整的版本" },
  { id: "shiji", repository: "https://github.com/baojie/shiji-kb", revision: "6b836e3fac1b900ccc6e9299fd6896c0e18ec132", revisionDate: "2026-08-31T01:54:33Z", license: "原文公有领域；剥离实体标注", note: "殆知阁史记文件缺少部分正文标题，故仅史记使用 GitHub 分篇原文" },
);
manifest.expansion = {
  previousCount: before,
  gushiwenRawCount: gushiwen.length,
  gushiwenAddedCount: gushiwenAdded,
  gushiwenDuplicateCount: gushiwenDuplicates,
  gushiwenEnrichedCount: gushiwenEnriched,
  nalanReplacementCount: nalanReplaced,
  addedCount: uniqueWorks.length - before,
  resultCount: uniqueWorks.length,
  note: "主原文库按作者与规范化全文去重并补充精读字段；纳兰性德以完整诗集为唯一正文版本，旧来源同作者条目整体替换；整书选用标题结构完整的 GitHub 存档，均以一部作品和内部章节保存。",
};
const expandedBookOrder = [
  ...books.map((book) => book.id),
  ...novels.map(([id]) => id),
  "shiji",
];
manifest.composition.classicCollections.push(...expandedBookOrder.map((id) => {
  const work = works.find((item) => item.id === id);
  return { id, title: work.title, sourceChapterCount: work.chapters.length, workCount: 1 };
}));
manifest.result.importedCount = uniqueWorks.length;
await Bun.write(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(JSON.stringify({ before, gushiwenAdded, gushiwenDuplicates, gushiwenEnriched, final: uniqueWorks.length, books: uniqueWorks.filter((work) => work.kind === "BOOK").map((work) => [work.title, work.chapters.length]) }, null, 2));
