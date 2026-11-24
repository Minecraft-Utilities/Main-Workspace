/**
 * Lookups of unknown players (Mojang round trip + create) while known players are read.
 * Usage: bun miss-mix.ts [seconds=30] [miss-clients=16] [hit-clients=4] [base=http://localhost:8080]
 * Reports p50/p99 for hits (the traffic that suffers when misses hold DB connections) and miss throughput.
 */
const seconds = Number(process.argv[2] ?? 30);
const missClients = Number(process.argv[3] ?? 16);
const hitClients = Number(process.argv[4] ?? 4);
const base = process.argv[5] ?? "http://localhost:8080";
const players = (await Bun.file(`${import.meta.dir}/work/read-ids.txt`).text())
  .trim().split("\n").map((l) => l.split("\t") as [string, string]);
const end = Date.now() + seconds * 1000;
const hex = () => Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16)).join("");
const hit: number[] = [], miss: number[] = [];
let hitErr = 0, missErr = 0;
async function run(path: () => string, out: number[], onErr: () => void) {
  while (Date.now() < end) {
    const t = performance.now();
    try {
      const res = await fetch(base + path());
      await res.arrayBuffer();
      if (res.status >= 500) { onErr(); }
    } catch { onErr(); }
    out.push(performance.now() - t);
  }
}
await Promise.all([
  ...Array.from({ length: missClients }, () => run(() => `/players/${hex()}`, miss, () => missErr++)),
  ...Array.from({ length: hitClients }, () => run(() => `/players/${players[Math.floor(Math.random() * players.length)][0]}`, hit, () => hitErr++)),
]);
const q = (a: number[], p: number) => +a.sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor(a.length * p))].toFixed(1);
console.log(JSON.stringify({
  seconds, missClients, hitClients,
  hit: { n: hit.length, p50: q(hit, 0.5), p99: q(hit, 0.99), errors: hitErr },
  miss: { n: miss.length, perSec: +(miss.length / seconds).toFixed(1), p50: q(miss, 0.5), p99: q(miss, 0.99), errors: missErr },
}, null, 1));
