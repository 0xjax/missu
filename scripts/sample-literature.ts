const root = new URL("..", import.meta.url).pathname.replace(/^\/(?:([A-Za-z]:))/, "$1");
const input = `${root}/.local/literature/ranked.json`;
const output = `${root}/.local/literature/range-sample.md`;
const start = 10_000;
const segmentSize = 1_000;
const sampleSize = 10;
const segmentCount = 10;
const seed = 20261121;

type Work = {
  id: string;
  title: string;
  author: string;
  text: string;
};

function random(seedValue: number) {
  let value = seedValue >>> 0;
  return () => {
    value += 0x6D2B79F5;
    let result = value;
    result = Math.imul(result ^ (result >>> 15), result | 1);
    result ^= result + Math.imul(result ^ (result >>> 7), result | 61);
    return ((result ^ (result >>> 14)) >>> 0) / 4294967296;
  };
}

const works = await Bun.file(input).json() as Work[];
const lines = [`# 文库第 ${start + 1}–${start + segmentSize * segmentCount} 名抽样`, "", `固定随机种子：${seed}；每段 1,000 篇抽 ${sampleSize} 篇。摘句为首句与末句，不代表人工认定的名句。`, ""];
for (let segment = 0; segment < segmentCount; segment++) {
  const from = start + segment * segmentSize;
  const pool = works.slice(from, from + segmentSize);
  if (pool.length < segmentSize) throw new Error(`Insufficient works at ${from}`);
  const rng = random(seed + segment);
  const shuffled = [...pool];
  for (let index = shuffled.length - 1; index > 0; index--) {
    const other = Math.floor(rng() * (index + 1));
    [shuffled[index], shuffled[other]] = [shuffled[other], shuffled[index]];
  }
  const samples = shuffled.slice(0, sampleSize)
    .map((work) => ({ work, rank: works.indexOf(work) + 1 }))
    .sort((a, b) => a.rank - b.rank);
  lines.push(`## 第 ${segment + 1} 段（第 ${from + 1}–${from + segmentSize} 名）`, "");
  samples.forEach(({ work, rank }, index) => {
    const workLines = work.text.split("\n").filter(Boolean);
    const first = workLines[0] ?? "";
    const last = workLines.at(-1) ?? first;
    lines.push(`${index + 1}. **${work.title}** — ${work.author}（第 ${rank} 名）`);
    lines.push(`   - 首句：${first}`);
    if (last !== first) lines.push(`   - 末句：${last}`);
  });
  lines.push("");
}
await Bun.write(output, `${lines.join("\n")}\n`);
console.log(output);
