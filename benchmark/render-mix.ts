/**
 * Skin render requests on mostly-cold skins (each request picks a random seeded player, part and size).
 * Usage: bun render-mix.ts [seconds=40] [concurrency=16] [base=http://localhost:8080]
 */
const seconds = Number(process.argv[2] ?? 40);
const concurrency = Number(process.argv[3] ?? 16);
const base = process.argv[4] ?? "http://localhost:8080";
const players = (await Bun.file(`${import.meta.dir}/work/read-ids.txt`).text())
  .trim().split("\n").map((l) => l.split("\t")[0]);
const parts = ["FACE", "BODY", "HEAD_ISO", "FULLBODY_ISO_FRONT"];
const sizes = [128, 256, 512];
const pick = <T,>(a: T[]) => a[Math.floor(Math.random() * a.length)];
const lat: number[] = [];
let errors = 0;
const end = Date.now() + seconds * 1000;
async function worker() {
  while (Date.now() < end) {
    const t = performance.now();
    try {
      const res = await fetch(`${base}/skins/${pick(players)}/${pick(parts)}.png?size=${pick(sizes)}`);
      await res.arrayBuffer();
      if (res.status >= 400) { errors++; }
    } catch { errors++; }
    lat.push(performance.now() - t);
  }
}
await Promise.all(Array.from({ length: concurrency }, worker));
lat.sort((a, b) => a - b);
const q = (p: number) => +lat[Math.min(lat.length - 1, Math.floor(lat.length * p))].toFixed(1);
console.log(JSON.stringify({ seconds, concurrency, requests: lat.length, rps: +(lat.length / seconds).toFixed(1), p50: q(0.5), p99: q(0.99), errors }, null, 1));
