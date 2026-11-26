/**
 * Cape/skin by-id request mix against a running backend.
 * Usage: bun id-mix.ts [seconds=40] [concurrency=8] [base=http://localhost:8080]
 * Needs work/cape-ids.txt and work/skin-ids.txt (one id per line); see bench-ids.sh.
 */
const seconds = Number(process.argv[2] ?? 40);
const concurrency = Number(process.argv[3] ?? 8);
const base = process.argv[4] ?? "http://localhost:8080";
const load = async (f: string) => (await Bun.file(`${import.meta.dir}/work/${f}`).text()).trim().split("\n");
const capes = await load("cape-ids.txt");
const skins = await load("skin-ids.txt");
const pick = <T,>(a: T[]) => a[Math.floor(Math.random() * a.length)];
const classes: Record<string, () => string> = {
  "cape by id": () => `/capes/${pick(capes)}`,
  "skin by id": () => `/skins/${pick(skins)}`,
};
const lat: Record<string, number[]> = { "cape by id": [], "skin by id": [] };
const errors: Record<string, number> = {};
const end = Date.now() + seconds * 1000;
async function worker() {
  while (Date.now() < end) {
    const name = Math.random() < 0.3 ? "cape by id" : "skin by id";
    const t = performance.now();
    try {
      const res = await fetch(base + classes[name]());
      await res.arrayBuffer();
      if (res.status >= 500) { errors[name] = (errors[name] ?? 0) + 1; }
    } catch { errors[name] = (errors[name] ?? 0) + 1; }
    lat[name].push(performance.now() - t);
  }
}
await Promise.all(Array.from({ length: concurrency }, worker));
const q = (a: number[], p: number) => a[Math.min(a.length - 1, Math.floor(a.length * p))];
const out: Record<string, unknown> = {};
for (const name of Object.keys(lat)) {
  const a = lat[name].sort((x, y) => x - y);
  out[name] = { n: a.length, p50: +q(a, 0.5).toFixed(1), p95: +q(a, 0.95).toFixed(1), max: +a[a.length - 1].toFixed(1), errors: errors[name] ?? 0 };
}
console.log(JSON.stringify({ seconds, concurrency, classes: out }));
