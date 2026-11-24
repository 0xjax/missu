import { mkdir, readdir } from "node:fs/promises";

const root = new URL("..", import.meta.url).pathname.replace(/^\/(?:([A-Za-z]:))/, "$1");
const sourceDirectory = `${root}/content/articles/sources`;
const manifestPath = `${root}/content/articles/manifest.json`;
const outputPath = `${root}/app/src/main/assets/articles.json`;

type Article = {
  id: string;
  category: string;
  title: string;
  summary: string;
  paragraphs: string[];
};

const files = (await readdir(sourceDirectory)).filter((file) => file.endsWith(".json")).sort();
if (files.length === 0) throw new Error("No public articles found");

const articles: Article[] = [];
const ids = new Set<string>();
for (const file of files) {
  const article = await Bun.file(`${sourceDirectory}/${file}`).json() as Article;
  if (!/^[a-z0-9-]+$/.test(article.id) || !ids.add(article.id)) {
    throw new Error(`Invalid or duplicate article id: ${article.id}`);
  }
  if (file !== `${article.id}.json`) throw new Error(`Article file does not match id: ${file}`);
  for (const [field, value] of Object.entries({
    category: article.category,
    title: article.title,
    summary: article.summary,
  })) {
    if (!value?.trim()) throw new Error(`Missing ${field}: ${article.id}`);
  }
  if (!Array.isArray(article.paragraphs) || article.paragraphs.length < 4 ||
    article.paragraphs.some((paragraph) => typeof paragraph !== "string" || !paragraph.trim())) {
    throw new Error(`Article needs at least four non-empty paragraphs: ${article.id}`);
  }
  validatePublicText(article.id, [
    article.category,
    article.title,
    article.summary,
    ...article.paragraphs,
  ].join("\n"));
  articles.push(article);
}

const output = `${JSON.stringify({ articles }, null, 2)}\n`;
const bytes = new TextEncoder().encode(output);
const manifest = {
  articleCount: articles.length,
  articles: articles.map(({ id, category, title }) => ({ id, category, title })),
  sha256: new Bun.CryptoHasher("sha256").update(bytes).digest("hex"),
};
await mkdir(`${root}/app/src/main/assets`, { recursive: true });
await Bun.write(outputPath, output);
await Bun.write(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Built ${outputPath}: ${articles.length} public articles, ${bytes.byteLength} bytes`);

function validatePublicText(id: string, text: string) {
  const forbidden = new Map<string, RegExp>([
    ["Obsidian link", /\[\[|\]\]/],
    ["Markdown file or front matter", /\.md\b|^---$/m],
    ["private source metadata", /protected\s*:|ob-vault|growth[\\/]/i],
    ["local path", /[A-Za-z]:[\\/]/],
    ["external URL", /https?:\/\/|www\./i],
    ["email address", /[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/],
    ["mainland China mobile number", /(?<!\d)1[3-9]\d{9}(?!\d)/],
    ["specific calendar date", /\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?/],
    ["specific monetary amount", /[¥￥]\s*\d|\d+(?:\.\d+)?\s*(?:元|万元)/],
  ]);
  for (const [label, pattern] of forbidden) {
    if (pattern.test(text)) throw new Error(`Public article ${id} contains ${label}`);
  }
}
