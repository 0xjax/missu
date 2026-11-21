import OpenCC from "opencc-js";
import { mkdir } from "node:fs/promises";

const root = new URL("..", import.meta.url).pathname.replace(/^\/(?:([A-Za-z]:))/, "$1");
const revision = "b8594f81a89752241442f2ce267d6f66f96704ee";
const upstream = `https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/${revision}`;
const cache = `${root}/.local/literature/source`;
const output = `${root}/.local/literature/imported.json`;
const baseOutput = `${root}/.local/literature/imported-base.json`;
const manifestPath = `${root}/content/literature/manifest.json`;
const convert = OpenCC.Converter({ from: "twp", to: "cn" });
const emptyText = { en: "", zh: "", "zh-Hant": "", ja: "" };
const emptyNotes = { en: [], zh: [], "zh-Hant": [], ja: [] };

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
  chapters: { id: string; title: string; text: string }[];
};

const paths = [
  "诗经/shijing.json",
  "论语/lunyu.json",
  "楚辞/chuci.json",
  "元曲/yuanqu.json",
  "纳兰性德/纳兰性德诗集.json",
  "蒙学/guwenguanzhi.json",
  "蒙学/baijiaxing.json",
  "蒙学/dizigui.json",
  "蒙学/qianziwen.json",
  "蒙学/sanzijing-new.json",
  "蒙学/shenglvqimeng.json",
  "蒙学/youxueqionglin.json",
  "蒙学/zengguangxianwen.json",
  "蒙学/zhuzijiaxun.json",
  ...Array.from({ length: 58 }, (_, index) => `全唐诗/poet.tang.${index * 1000}.json`),
  ...Array.from({ length: 58 }, (_, index) => `rank/poet/poet.tang.rank.${index * 1000}.json`),
  ...Array.from({ length: 22 }, (_, index) => `宋词/ci.song.${index * 1000}.json`),
  ...Array.from({ length: 22 }, (_, index) => `rank/ci/ci.song.rank.${index * 1000}.json`),
];

async function fetchCached(path: string) {
  const target = `${cache}/${path.replaceAll("/", "__")}`;
  if (await Bun.file(target).exists()) return target;
  const url = `${upstream}/${encodeURI(path)}`;
  let error: unknown;
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const bytes = await response.arrayBuffer();
      await Bun.write(target, bytes);
      return target;
    } catch (current) {
      error = current;
      await Bun.sleep(attempt * 1000);
    }
  }
  throw new Error(`Download failed: ${path}: ${error}`);
}

await mkdir(cache, { recursive: true });
let cursor = 0;
let completed = 0;
await Promise.all(Array.from({ length: 4 }, async () => {
  while (cursor < paths.length) {
    const path = paths[cursor++];
    await fetchCached(path);
    completed++;
    if (completed % 10 === 0 || completed === paths.length) console.log(`Downloaded ${completed}/${paths.length}`);
  }
}));

async function fetchLongyushengDetail(url: string): Promise<string> {
  const detailCache = `${root}/.local/literature/longyusheng-details`;
  await mkdir(detailCache, { recursive: true });
  const name = url.replace(/^https:\/\/longyusheng\.org\//, "").replaceAll("/", "__");
  const target = `${detailCache}/${name}`;
  if (!await Bun.file(target).exists()) {
    let error: unknown;
    for (let attempt = 1; attempt <= 4; attempt++) {
      try {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        await Bun.write(target, new TextDecoder("gb18030").decode(await response.arrayBuffer()));
        break;
      } catch (current) {
        error = current;
        await Bun.sleep(attempt * 1000);
      }
    }
    if (!await Bun.file(target).exists()) throw new Error(`Download failed: ${url}: ${error}`);
  }
  const html = await Bun.file(target).text();
  const body = html.match(/<div class="ci"><div class="body">([\s\S]*?)<\/div><\/div><\/div>/)?.[1];
  if (!body) throw new Error(`Missing work body: ${url}`);
  return body.split(/<\/div>/).map((line) => line.replace(/<[^>]+>/g, "").trim())
    .filter(Boolean).join("\n");
}

function score(rank: Record<string, unknown> | null): number {
  if (!rank) return 0;
  const value = ["baidu", "so360", "google", "bing", "bing_en", "bing_cn", "bing_global"].reduce((sum, key) => {
    const count = Number(rank[key] ?? 0);
    return sum + (Number.isFinite(count) ? Math.log1p(count) : 0);
  }, 0);
  return Math.round(value * 1_000_000);
}

function work(
  id: string,
  kind: string,
  title: string,
  author: string,
  paragraphs: string[],
  source: string,
  prominence: number,
  canonical = false,
): Work {
  const convertedAuthor = convert(author).trim();
  return {
    id,
    kind,
    title: convert(title).trim() || "无题",
    author: convertedAuthor.endsWith("·") ? `${convertedAuthor} 佚名` : convertedAuthor,
    text: convert(paragraphs.join("\n")).trim(),
    source,
    prominence,
    theme: { ...emptyText },
    notes: { ...emptyNotes },
    translation: { ...emptyText },
    appreciation: { ...emptyText },
    canonical,
    chapters: [],
  };
}

function book(
  id: string,
  title: string,
  author: string,
  source: string,
  entries: { title: string; text: string }[],
): Work {
  if (!entries.length || entries.some((entry) => !entry.text.trim())) {
    throw new Error(`Invalid book chapters: ${title}`);
  }
  const result = work(id, "BOOK", title, author, [], source, 1_600_000_000, true);
  result.chapters = entries.map((entry, index) => ({
    id: `${id}-${index + 1}`,
    title: convert(entry.title).trim(),
    text: convert(entry.text).trim(),
  }));
  return result;
}

function rankMatcher(ranks: any[], field: "title" | "rhythmic") {
  const byWork = new Map<string, any[]>();
  ranks.forEach((rank) => {
    String(rank[field]).split(/[・\/]/).forEach((title: string) => {
      const key = `${title}|${rank.author}`;
      const queue = byWork.get(key) ?? [];
      queue.push(rank);
      byWork.set(key, queue);
    });
  });
  return (poem: any) => {
    const title = poem[field] as string;
    const author = poem.author;
    const baseTitle = title.replace(/\s+(?:其)?[一二三四五六七八九十百千万零〇0-9]+$/, "");
    const rank = byWork.get(`${title}|${author}`)?.shift()
      ?? byWork.get(`${baseTitle}|${author}`)?.shift();
    return rank ?? null;
  };
}

function matchKey(author: string, phrase: string): string {
  const plainAuthor = author.split("·").pop()?.trim() ?? author;
  return `${convert(plainAuthor)}|${convert(phrase).replace(/[^\p{L}\p{N}]/gu, "")}`;
}

const ranked: Work[] = [];
let tangRawCount = 0;
let ciRawCount = 0;
for (let index = 0; index < 58; index++) {
  const poems = await Bun.file(await fetchCached(`全唐诗/poet.tang.${index * 1000}.json`)).json();
  const ranks = await Bun.file(await fetchCached(`rank/poet/poet.tang.rank.${index * 1000}.json`)).json();
  tangRawCount += poems.length;
  const rankFor = rankMatcher(ranks, "title");
  poems.forEach((poem: any, item: number) => {
    ranked.push(work(
      `tang-${poem.id}`,
      "POEM",
      poem.title,
      `唐 · ${poem.author}`,
      poem.paragraphs,
      "chinese-poetry · 全唐诗",
      score(rankFor(poem)),
      poem.tags?.includes("唐诗三百首") ?? false,
    ));
  });
}
for (let index = 0; index < 22; index++) {
  const poems = await Bun.file(await fetchCached(`宋词/ci.song.${index * 1000}.json`)).json();
  const ranks = await Bun.file(await fetchCached(`rank/ci/ci.song.rank.${index * 1000}.json`)).json();
  ciRawCount += poems.length;
  const rankFor = rankMatcher(ranks, "rhythmic");
  poems.forEach((poem: any, item: number) => {
    const firstPhrase = poem.paragraphs.join("").split(/[，。！？；：]/).find(Boolean) ?? poem.rhythmic;
    ranked.push(work(
      `ci-song-${index * 1000 + item}`,
      "CI",
      `${poem.rhythmic} · ${firstPhrase}`,
      `宋 · ${poem.author}`,
      poem.paragraphs,
      "chinese-poetry · 全宋词",
      score(rankFor(poem)),
      poem.tags?.includes("宋词三百首") ?? false,
    ));
  });
}
const eligible = ranked.filter((work) => work.text.length > 0);
const byProminence = (a: Work, b: Work) => b.prominence - a.prominence || a.id.localeCompare(b.id);
const rankedAll = eligible.sort(byProminence);
const longyusheng = await Bun.file(`${root}/content/literature/anthologies/longyusheng.json`).json();
const workByKey = new Map<string, Work>();
const workByFullText = new Map<string, Work>();
rankedAll.forEach((work) => {
  const firstPhrase = work.text.split(/[\n，。！？；：]/).find(Boolean) ?? work.title;
  workByKey.set(matchKey(work.author, firstPhrase), work);
  workByFullText.set(matchKey(work.author, work.text), work);
});
longyusheng.entries.forEach((entry: any) => {
  const work = workByKey.get(matchKey(entry.author, entry.firstPhrase));
  entry.workId = work?.id ?? null;
  entry.rank = work ? rankedAll.indexOf(work) + 1 : null;
  if (work) work.canonical = true;
});
const anthologyOnlyWorks: Work[] = [];
for (const entry of longyusheng.entries) {
  if (entry.workId != null) continue;
  const text = await fetchLongyushengDetail(entry.detailUrl);
  const existing = workByFullText.get(matchKey(entry.author, text));
  if (existing) {
    entry.workId = existing.id;
    entry.rank = rankedAll.indexOf(existing) + 1;
    existing.canonical = true;
    continue;
  }
  const supplemental = work(
    entry.id,
    "CI",
    entry.title,
    entry.author,
    text.split("\n"),
    "龙榆生 · 唐宋名家词选",
    0,
    true,
  );
  anthologyOnlyWorks.push(supplemental);
  entry.workId = supplemental.id;
}
const matchedEntries = longyusheng.entries.filter((entry: any) => entry.workId != null);
const anthologyMaxRank = Math.max(0, ...matchedEntries
  .filter((entry: any) => entry.rank != null && rankedAll[entry.rank - 1].prominence > 0)
  .map((entry: any) => entry.rank));
const adoptedRankedLimit = rankedAll.length;
const canonical = rankedAll.filter((work) => work.canonical);
const rankedSelection = rankedAll.slice(0, adoptedRankedLimit);
const rankedIds = new Set(rankedSelection.map((work) => work.id));
const sharedRankedAndAnthologyCount = canonical.filter((work) => rankedIds.has(work.id)).length;
const anthologyUnionCount = canonical.length + anthologyOnlyWorks.length;
const selected = [
  ...rankedSelection,
  ...canonical,
  ...anthologyOnlyWorks,
].filter((work, index, all) => all.findIndex((candidate) => candidate.id === work.id) === index);
await Bun.write(`${root}/.local/literature/ranked.json`, `${JSON.stringify(rankedAll, null, 2)}\n`);
await Bun.write(`${root}/content/literature/anthologies/longyusheng.json`, `${JSON.stringify(longyusheng, null, 2)}\n`);

const shijing = await Bun.file(await fetchCached("诗经/shijing.json")).json();
const commonShijing = new Set(["关雎", "蒹葭", "氓", "采薇", "硕鼠", "伐檀", "君子于役", "静女", "无衣", "鹿鸣", "鹤鸣", "木瓜", "子衿", "桃夭", "击鼓", "式微", "黍离", "风雨", "七月", "东山"]);
shijing.forEach((poem: any, index: number) => {
  selected.push(work(
    `shijing-${index}`,
    "POEM",
    poem.title,
    "先秦 · 佚名",
    poem.content,
    `诗经 · ${poem.section} · ${poem.chapter}`,
    commonShijing.has(poem.title) ? 1_800_000_000 - index : 1_000_000 - index,
  ));
});
const lunyu = await Bun.file(await fetchCached("论语/lunyu.json")).json();
const lunyuBook = work(
  "lunyu",
  "BOOK",
  "论语",
  "春秋 · 孔子及其弟子",
  [],
  "chinese-poetry · 论语",
  1_700_000_000,
);
lunyuBook.chapters = lunyu.map((chapter: any, index: number) => ({
  id: `lunyu-${index + 1}`,
  title: chapter.chapter,
  text: chapter.paragraphs.join("\n"),
}));
selected.push(lunyuBook);

const chuci = await Bun.file(await fetchCached("楚辞/chuci.json")).json();
selected.push(book(
  "chuci",
  "楚辞",
  "战国 · 屈原 等",
  "chinese-poetry · 楚辞",
  chuci.map((chapter: any) => ({
    title: `${chapter.section} · ${chapter.title}`,
    text: chapter.content.join("\n"),
  })),
));

const guwenguanzhi = await Bun.file(await fetchCached("蒙学/guwenguanzhi.json")).json();
const guwenChapters = guwenguanzhi.content.flatMap((volume: any) => volume.content.map((chapter: any) => ({
  title: `${volume.title} · ${chapter.chapter}`,
  text: chapter.paragraphs.join("\n"),
})));
if (guwenChapters.length !== 222) throw new Error(`Unexpected 古文观止 chapter count: ${guwenChapters.length}`);
selected.push(book(
  "guwen-guanzhi",
  "古文观止",
  "清 · 吴楚材、吴调侯 编",
  "chinese-poetry · 古文观止",
  guwenChapters,
));

const primerSpecs = [
  ["baijiaxing", "百家姓", "北宋 · 佚名"],
  ["dizigui", "弟子规", "清 · 李毓秀"],
  ["qianziwen", "千字文", "南朝梁 · 周兴嗣"],
  ["sanzijing-new", "三字经", "南宋 · 王应麟"],
  ["shenglvqimeng", "声律启蒙", "清 · 车万育"],
  ["youxueqionglin", "幼学琼林", "明 · 程登吉"],
  ["zengguangxianwen", "增广贤文", "明清 · 佚名"],
  ["zhuzijiaxun", "朱子家训", "清 · 朱柏庐"],
] as const;
const primerChapterCounts = new Map<string, number>();
for (const [path, title, author] of primerSpecs) {
  const json = await Bun.file(await fetchCached(`蒙学/${path}.json`)).json();
  const sections = json.paragraphs ? [{ title: "全文", paragraphs: json.paragraphs }] : json.content;
  const chapters = sections.flatMap((section: any) => {
    if (section.paragraphs) {
      return [{ title: section.chapter ?? section.title ?? title, text: section.paragraphs.join("\n") }];
    }
    return section.content.map((chapter: any) => ({
      title: `${section.title} · ${chapter.chapter}`,
      text: chapter.paragraphs.join("\n"),
    }));
  });
  primerChapterCounts.set(path, chapters.length);
  selected.push(book(path, title, author, `chinese-poetry · ${title}`, chapters));
}

const yuanqu = await Bun.file(await fetchCached("元曲/yuanqu.json")).json();
let yuanquSkipped = 0;
yuanqu.forEach((qu: any, index: number) => {
  if (!qu.paragraphs?.some((line: string) => line.trim())) {
    yuanquSkipped++;
    return;
  }
  selected.push(work(
    `yuanqu-${index + 1}`,
    "QU",
    convert(qu.title).replaceAll("・", " · ").replaceAll("/", " / ").trim(),
    `元 · ${qu.author}`,
    qu.paragraphs,
    "chinese-poetry · 元曲",
    0,
  ));
});

const nalan = await Bun.file(await fetchCached("纳兰性德/纳兰性德诗集.json")).json();
const nalanCiTitles = new Set(selected.filter((work) => work.kind === "CI").map((work) => work.title.split("·")[0].trim()));
nalan.forEach((poem: any, index: number) => {
  const title = convert(poem.title).trim();
  selected.push(work(
    `nalan-${index + 1}`,
    nalanCiTitles.has(title.split("·")[0].trim()) ? "CI" : "POEM",
    title,
    "清 · 纳兰性德",
    poem.para,
    "chinese-poetry · 纳兰性德诗集",
    0,
  ));
});

const serialized = `${JSON.stringify(selected, null, 2)}\n`;
await Bun.write(baseOutput, serialized);
await Bun.write(output, serialized);
const selectedCount = (source: string) => selected.filter((work) => work.source === source).length;
const structuredBooks = [
  { id: "lunyu", sourceChapterCount: lunyu.length },
  { id: "chuci", sourceChapterCount: chuci.length },
  { id: "guwen-guanzhi", sourceChapterCount: guwenChapters.length },
  ...primerSpecs.map(([path]) => ({ id: path, sourceChapterCount: primerChapterCounts.get(path)! })),
].map((entry) => ({
  ...entry,
  title: selected.find((work) => work.id === entry.id)!.title,
  workCount: 1,
}));
await Bun.write(manifestPath, `${JSON.stringify({
  schemaVersion: 1,
  databaseVersion: 4,
  sources: [
    {
      id: "chinese-poetry",
      repository: "https://github.com/chinese-poetry/chinese-poetry",
      revision,
      revisionDate: "2026-06-17T01:56:08Z",
      license: "MIT",
      datasets: [
        { id: "tang", rawCount: tangRawCount, selectedCount: selectedCount("chinese-poetry · 全唐诗") },
        { id: "song-ci", rawCount: ciRawCount, selectedCount: selectedCount("chinese-poetry · 全宋词") },
        { id: "shijing", rawCount: shijing.length, selectedCount: shijing.length },
        ...structuredBooks.map((entry) => ({
          id: entry.id,
          rawCount: entry.sourceChapterCount,
          selectedCount: 1,
        })),
        { id: "yuanqu", rawCount: yuanqu.length, selectedCount: selectedCount("chinese-poetry · 元曲"), skippedEmptyCount: yuanquSkipped },
        { id: "nalan", rawCount: nalan.length, selectedCount: selectedCount("chinese-poetry · 纳兰性德诗集") },
      ],
    },
    {
      id: "longyusheng-tangsong-mingjia",
      title: "唐宋名家词选",
      editor: "龙榆生",
      url: "https://longyusheng.org/tangsongci/mulu.html",
      pageUpdatedAt: "2026-01-18",
      retrievedAt: "2026-11-21",
      role: "选本目录",
    },
  ],
  selection: {
    minimumRankedLimit: 10_000,
    anthologyMaxRank,
    adoptedRankedLimit,
    rule: "先以 N = max(10000, Rmax) 计算候选范围；本次在候选范围接近完整语料后采用完整语料，再与选本、典籍和本地内容取并集",
    rationale: `选本决定价值基线，常见度排名决定补充范围。候选边界 ${anthologyMaxRank} 由完整覆盖选本计算得出，已接近完整唐宋语料 ${rankedAll.length} 篇；为消除截断并保留完整来源，本次采用完整语料，而不是直接按篇数凑量。`,
    anthologiesAlwaysIncluded: ["唐诗三百首", "宋词三百首", "唐宋名家词选"],
    longyushengEntries: longyusheng.entries.length,
    longyushengMatched: matchedEntries.length,
    longyushengUnmatched: longyusheng.entries.length - matchedEntries.length,
    longyushengSupplemental: anthologyOnlyWorks.length,
    ranking: "各搜索引擎结果数分别取 log1p 后求和；词牌别名共享排名",
    missingRank: "选本作品仍保留，但不参与 Rmax 计算；其他作品 prominence = 0",
  },
  composition: {
    rankedRangeCount: rankedSelection.length,
    anthologyUnionCount,
    sharedRankedAndAnthologyCount,
    rankedOnlyCount: rankedSelection.length - sharedRankedAndAnthologyCount,
    anthologyBeyondRangeCount: anthologyUnionCount - sharedRankedAndAnthologyCount,
    longyushengUniqueWorkCount: new Set(matchedEntries.map((entry: any) => entry.workId)).size,
    anthologySupplementalWorks: anthologyOnlyWorks.map((work) => ({
      id: work.id,
      title: work.title,
      author: work.author,
      source: work.source,
    })),
    classicCollections: structuredBooks,
    notes: "选本并集与排名范围取并集；详情页补充作品只计算一次。诗经、典籍及本地精读另列来源，不与选本补充重复计算。典籍按一部作品及内部篇章收录，原始篇章数与库内作品数分别记录。", 
  },
  transformation: {
    traditionalToSimplified: "opencc-js@1.4.2",
  },
  result: {
    rawCount: tangRawCount + ciRawCount + shijing.length + yuanqu.length + nalan.length + structuredBooks.reduce((sum, entry) => sum + entry.sourceChapterCount, 0),
    importedCount: selected.length,
    canonicalCount: selected.filter((work) => work.canonical).length,
  },
}, null, 2)}\n`);
console.log(`Imported ${selected.length} works to ${output}`);
