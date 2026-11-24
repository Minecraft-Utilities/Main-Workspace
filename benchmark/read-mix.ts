/**
 * Read-path request mix against a running backend.
 * Usage: bun read-mix.ts [seconds=60] [concurrency=8] [base=http://localhost:8080]
 * Needs work/read-ids.txt (player id and username per line, tab separated); see bench-read.sh.
 * Reports p50/p99/max per request class.
 */
const seconds = Number(process.argv[2] ?? 60);
const concurrency = Number(process.argv[3] ?? 8);
const base = process.argv[4] ?? "http://localhost:8080";
const players = (await Bun.file(`${import.meta.dir}/work/read-ids.txt`).text())
  .trim().split("\n").map((l) => l.split("\t") as [string, string]);

const pick = <T,>(a: T[]) => a[Math.floor(Math.random() * a.length)];
const classes: Record<string, () => string> = {
  "player by uuid": () => `/players/${pick(players)[0]}`,
  "player by name": () => `/players/${pick(players)[1]}`,
  "player full": () => `/players/${pick(players)[0]}?type=FULL`,
  "search common prefix": () => `/players?query=${pick(players)[1].slice(0, 4)}`,
  "search rare prefix": () => `/players?query=zq${Math.random().toString(36).slice(2, 6)}`,
  "recent name changes": () => `/players/name-changes`,
};
const weights: [string, number][] = [
  ["player by uuid", 30], ["player by name", 30], ["player full", 15],
  ["search common prefix", 15], ["search rare prefix", 5], ["recent name changes", 5],
];
const total = weights.reduce((s, [, w]) => s + w, 0);
const choose = () => {
  let r = Math.random() * total;
  for (const [name, w] of weights) { if ((r -= w) < 0) return name; }
  return weights[0][0];
};

const lat: Record<string, number[]> = {};
const errors: Record<string, number> = {};
const end = Date.now() + seconds * 1000;
async function worker() {
  while (Date.now() < end) {
    const name = choose();
    const t = performance.now();
    try {
      const res = await fetch(base + classes[name]());
      await res.arrayBuffer();
      if (res.status >= 500) { errors[name] = (errors[name] ?? 0) + 1; }
    } catch { errors[name] = (errors[name] ?? 0) + 1; }
    (lat[name] ??= []).push(performance.now() - t);
  }
}
await Promise.all(Array.from({ length: concurrency }, worker));

const q = (a: number[], p: number) => a[Math.min(a.length - 1, Math.floor(a.length * p))];
const out: Record<string, unknown> = {};
let n = 0;
for (const [name] of weights) {
  const a = (lat[name] ?? []).sort((x, y) => x - y);
  n += a.length;
  out[name] = { n: a.length, p50: +q(a, 0.5).toFixed(1), p99: +q(a, 0.99).toFixed(1), max: +a[a.length - 1].toFixed(1), errors: errors[name] ?? 0 };
}
console.log(JSON.stringify({ seconds, concurrency, requests: n, rps: +(n / seconds).toFixed(1), classes: out }, null, 1));
