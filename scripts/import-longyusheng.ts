import { mkdir } from "node:fs/promises";

const root = new URL("..", import.meta.url).pathname.replace(/^\/(?:([A-Za-z]:))/, "$1");
const base = "https://longyusheng.org";
const cache = `${root}/.local/literature/longyusheng`;
const output = `${root}/content/literature/anthologies/longyusheng.json`;

function decode(value: string) {
  return value
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#xD;|&#xA;|\r/g, "")
    .trim();
}

async function fetchCached(path: string) {
  const target = `${cache}/${path.replaceAll("/", "__")}`;
  if (!await Bun.file(target).exists()) {
    const response = await fetch(`${base}/${path}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${path}`);
    await Bun.write(target, new TextDecoder("gb18030").decode(await response.arrayBuffer()));
  }
  return Bun.file(target).text();
}

await mkdir(cache, { recursive: true });
const catalog = await fetchCached("tangsongci/mulu.html");
const authors = [...catalog.matchAll(/<span class="listitem"><a href="\.\.\/(ci\/[^"]+)\/index\.html">([\s\S]*?)<\/a>/g)]
  .map((match) => ({ slug: match[1].split("/")[1], path: `${match[1]}/index.html`, name: decode(match[2]).replace(/\s/g, "") }));
if (authors.length < 90) throw new Error(`Unexpected catalog size: ${authors.length}`);

type Entry = { id: string; title: string; author: string; firstPhrase: string; url: string; detailUrl: string };
const entries: Entry[] = [];
let cursor = 0;
let completed = 0;
await Promise.all(Array.from({ length: 4 }, async () => {
  while (cursor < authors.length) {
    const author = authors[cursor++];
    const html = await fetchCached(author.path);
    const blocks = html.match(/<span class="listitem">[\s\S]*?<\/span><\/div>/g) ?? [];
    blocks.forEach((block) => {
      const titleMatch = block.match(/<span class="listitem"><a href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/);
      if (!titleMatch) return;
      const rhythmic = decode(titleMatch[2]).replace(/\s/g, "");
      const phrases = [...block.matchAll(/（([^（）]+)）/g)].map((match) => decode(match[1]));
      phrases.forEach((phrase) => {
        entries.push({
          id: `longyusheng-${author.slug}-${entries.length}`,
          title: `${rhythmic} · ${phrase}`,
          author: author.name,
          firstPhrase: phrase,
          url: `${base}/${author.path}`,
          detailUrl: new URL(titleMatch[1], `${base}/${author.path}`).href,
        });
      });
    });
    completed++;
    if (completed % 10 === 0 || completed === authors.length) console.log(`Read ${completed}/${authors.length} authors`);
  }
}));
await mkdir(`${root}/content/literature/anthologies`, { recursive: true });
await Bun.write(output, `${JSON.stringify({
  id: "longyusheng-tangsong-mingjia",
  title: "唐宋名家词选",
  editor: "龙榆生",
  url: `${base}/tangsongci/mulu.html`,
  pageUpdatedAt: "2026-01-18",
  retrievedAt: "2026-11-21",
  entries,
}, null, 2)}\n`);
console.log(`Imported ${entries.length} entries from ${authors.length} authors`);
