import { Database } from "bun:sqlite";
import { mkdir, unlink } from "node:fs/promises";

const root = new URL("..", import.meta.url).pathname.replace(/^\/(?:([A-Za-z]:))/, "$1");
const sourcePath = `${root}/content/literature/works.json`;
const outputPath = `${root}/app/src/main/assets/literature.db`;
const databaseVersion = 5;
const schemaDirectory = `${root}/app/schemas/app.missu.data.literature.LiteratureDatabase`;
const schemaPath = `${schemaDirectory}/${databaseVersion}.json`;
const locales = ["en", "zh", "zh-Hant", "ja"];
const kinds = new Set(["POEM", "CI", "QU", "PROSE", "BOOK"]);

type Localized = Record<string, string>;
type Work = {
  id: string;
  kind: string;
  title: string;
  author: string;
  text: string;
  chapters?: { id: string; title: string; text: string }[];
  source?: string;
  prominence?: number;
  theme: Localized;
  notes: Record<string, string[]>;
  translation: Localized;
  appreciation: Localized;
};

const baseWorks = await Bun.file(sourcePath).json() as Work[];
const supplementalPath = `${root}/content/literature/supplemental.json`;
const supplementalWorks = await Bun.file(supplementalPath).json() as Work[];
const curatedWorks = [...baseWorks, ...supplementalWorks];
const manifestPath = `${root}/content/literature/manifest.json`;
const manifest = await Bun.file(manifestPath).json();
const importedPath = `${root}/.local/literature/imported.json`;
if (!await Bun.file(importedPath).exists()) {
  throw new Error("Missing imported corpus; run bun scripts/import-literature.ts first");
}
const importedWorks = await Bun.file(importedPath).json() as Work[];
if (importedWorks.length !== manifest.result.importedCount) {
  throw new Error(`Imported corpus count mismatch: ${importedWorks.length} != ${manifest.result.importedCount}`);
}
const curatedKeys = new Set(curatedWorks.map((work) => workKey(work)));
const works = [
  ...curatedWorks,
  ...importedWorks.filter((work) => !curatedKeys.has(workKey(work))),
];
const ids = new Set<string>();
for (const work of works) {
  if (!/^[a-z0-9-]+$/.test(work.id) || !ids.add(work.id)) throw new Error(`Invalid or duplicate id: ${work.id}`);
  if (!kinds.has(work.kind)) throw new Error(`Invalid kind: ${work.id}`);
  for (const field of ["title", "author", "text"] as const) {
    if (field === "text" && work.kind === "BOOK") continue;
    if (!work[field].trim()) throw new Error(`Missing ${field}: ${work.id}`);
  }
  if (work.kind === "BOOK") {
    if (!work.chapters?.length) throw new Error(`Missing book chapters: ${work.id}`);
    const chapterIds = new Set<string>();
    for (const chapter of work.chapters) {
      if (!chapter.id.trim() || !chapter.title.trim() || !chapter.text.trim()) {
        throw new Error(`Invalid book chapter: ${work.id}/${chapter.id}`);
      }
      if (!chapterIds.add(chapter.id)) throw new Error(`Duplicate chapter id: ${work.id}/${chapter.id}`);
    }
  }
  for (const field of ["theme", "notes", "translation", "appreciation"] as const) {
    for (const locale of locales) {
      const value = work[field][locale];
      if (value == null) throw new Error(`Missing ${field}/${locale}: ${work.id}`);
    }
  }
}

function workKey(work: Work): string {
  const title = work.title.replace(/[\s·]/g, "");
  const author = work.author.split("·").pop()?.trim() ?? work.author;
  return `${title}|${author}`;
}

function tokenize(value: string): string[] {
  const normalized = value.toLocaleLowerCase();
  const tokens = new Set<string>();
  for (const match of normalized.matchAll(/[\p{Script=Han}]+|[\p{Letter}\p{Number}]+/gu)) {
    const part = match[0];
    if (/^\p{Script=Han}+$/u.test(part)) {
      const characters = [...part];
      characters.forEach((character) => tokens.add(character));
      for (let index = 0; index < characters.length - 1; index++) {
        tokens.add(characters[index] + characters[index + 1]);
      }
    } else {
      tokens.add(part);
    }
  }
  return [...tokens];
}

let identityHash = "";
for (const path of [schemaPath, `${schemaDirectory}/4.json`, `${schemaDirectory}/3.json`, `${schemaDirectory}/2.json`, `${schemaDirectory}/1.json`]) {
  if (await Bun.file(path).exists()) {
    identityHash = (await Bun.file(path).json()).database.identityHash;
    break;
  }
}
await mkdir(`${root}/app/src/main/assets`, { recursive: true });
await unlink(outputPath).catch(() => {});
const db = new Database(outputPath, { create: true });
db.exec(`
  PRAGMA user_version = ${databaseVersion};
  CREATE TABLE works (
    id TEXT NOT NULL,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    text TEXT NOT NULL,
    chapters TEXT NOT NULL,
    source TEXT NOT NULL,
    prominence INTEGER NOT NULL,
    theme TEXT NOT NULL,
    notes TEXT NOT NULL,
    translation TEXT NOT NULL,
    appreciation TEXT NOT NULL,
    PRIMARY KEY(id)
  );
  CREATE INDEX index_works_kind ON works(kind);
  CREATE INDEX index_works_prominence ON works(prominence);
  CREATE VIRTUAL TABLE IF NOT EXISTS \`works_search\` USING FTS5(\`tokens\`, tokenize=\`unicode61\`);
  CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
`);
const insertWork = db.prepare(`
  INSERT INTO works (id, kind, title, author, text, chapters, source, prominence, theme, notes, translation, appreciation)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
const insertSearch = db.prepare("INSERT INTO works_search (rowid, tokens) VALUES (?, ?)");
db.transaction((items: Work[]) => {
  for (const work of items) {
    const result = insertWork.run(
      work.id,
      work.kind,
      work.title,
      work.author,
      work.text,
      JSON.stringify(work.chapters ?? []),
      work.source ?? "Missu",
      work.prominence ?? 2_000_000_000,
      JSON.stringify(work.theme),
      JSON.stringify(work.notes),
      JSON.stringify(work.translation),
      JSON.stringify(work.appreciation),
    );
    const searchText = [work.title, work.author, work.text, ...(work.chapters ?? []).map((chapter) => chapter.text)].join(" ");
    insertSearch.run(Number(result.lastInsertRowid), tokenize(searchText).join(" "));
  }
})(works);
db.prepare("INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)").run(identityHash);
db.exec("PRAGMA optimize");
db.close();
const databaseSha256 = new Bun.CryptoHasher("sha256")
  .update(new Uint8Array(await Bun.file(outputPath).arrayBuffer()))
  .digest("hex");
manifest.localSources = [
  { path: "content/literature/works.json", count: baseWorks.length },
  { path: "content/literature/supplemental.json", count: supplementalWorks.length },
];
manifest.result = {
  ...manifest.result,
  curatedCount: baseWorks.length,
  supplementalCount: supplementalWorks.length,
  duplicateCount: curatedWorks.length + importedWorks.length - works.length,
  finalCount: works.length,
  byKind: Object.fromEntries([...kinds].map((kind) => [kind, works.filter((work) => work.kind === kind).length])),
  databaseSha256,
};
await Bun.write(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Built ${outputPath}: ${works.length} works, identity hash ${identityHash || "pending Room schema"}`);
