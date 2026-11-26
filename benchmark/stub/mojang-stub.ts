/**
 * Stub of the Mojang session/API/textures servers for benchmarking player refreshes.
 *
 * Answers for any UUID produced by seed.sh by deriving the profile from md5(uuid). A deterministic slice of
 * players "changed" their name, skin or cape, so the first refresh persists the change and later refreshes
 * see none (no flapping).
 *
 * Run: bun benchmark/stub/mojang-stub.ts [--port 9200] [--skins 19800000] [--latency-ms 0]
 *      [--rename-rate 0.002] [--skin-rate 0.02] [--cape-rate 0.003] [--error-rate 0] [--name-claim-rate 0.01]
 *
 * POST /profiles/minecraft (bulk name lookup) claims a deterministic slice of names, so name sweeps see
 * both unclaimed names and new accounts.
 */
import { createHash } from "node:crypto";
import { deflateSync } from "node:zlib";

const args = new Map<string, string>();
for (let i = 2; i < process.argv.length; i += 2) {
  args.set(process.argv[i], process.argv[i + 1]);
}
const opt = (name: string, fallback: number) => Number(args.get(`--${name}`) ?? fallback);

const port = opt("port", 9200);
const skins = opt("skins", 19_800_000);
const latencyMs = opt("latency-ms", 0);
const renameRate = opt("rename-rate", 0.002);
const skinRate = opt("skin-rate", 0.02);
const capeRate = opt("cape-rate", 0.003);
const errorRate = opt("error-rate", 0);
const nameClaimRate = opt("name-claim-rate", 0.01);
const CAPES = 44;

const md5 = (input: string) => createHash("md5").update(input).digest("hex");
const hex8 = (hash: string, offset: number) => parseInt(hash.slice(offset, offset + 8), 16);
const roll = (hash: string, offset: number, rate: number) => hex8(hash, offset) % 1_000_000 < Math.floor(rate * 1_000_000);

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  return c >>> 0;
});
const crc32 = (buf: Uint8Array) => {
  let c = 0xffffffff;
  for (const b of buf) {
    c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type: string, data: Uint8Array) => {
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, "ascii");
  Buffer.from(data).copy(out, 8);
  out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length);
  return out;
};
const skinPng = (() => {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(64, 0);
  ihdr.writeUInt32BE(64, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const raw = Buffer.alloc(64 * (1 + 64 * 4));
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", new Uint8Array()),
  ]);
})();

let profileRequests = 0;
let bulkNameRequests = 0;
let errors = 0;

function profile(raw: string): Response {
  const dashed = `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
  const h = md5(dashed);
  const h2 = md5(h);

  const username = roll(h2, 16, renameRate) ? `r${h2.slice(0, 12)}` : `u${h.slice(0, 12)}`;

  const skinIdx = 1 + (hex8(h, 0) % skins);
  const skinTexture = roll(h2, 0, skinRate) ? md5(`n${h}`) + md5(`N${h}`) : md5(`s${skinIdx}`) + md5(`S${skinIdx}`);
  const skin: Record<string, unknown> = { url: `http://textures.minecraft.net/texture/${skinTexture}` };
  if (skinIdx % 4 === 0) {
    skin.metadata = { model: "slim" };
  }

  let capeIdx = hex8(h, 8) % 1000 < 143 ? 1 + (hex8(h, 16) % CAPES) : 0;
  if (roll(h2, 8, capeRate)) {
    const other = 1 + (hex8(h2, 24) % CAPES);
    capeIdx = other === capeIdx ? 1 + (other % CAPES) : other;
  }
  const textures: Record<string, unknown> = { SKIN: skin };
  if (capeIdx > 0) {
    textures.CAPE = { url: `http://textures.minecraft.net/texture/${md5(`c${capeIdx}`)}${md5(`C${capeIdx}`)}` };
  }

  const decoded = JSON.stringify({ timestamp: 1700000000000, profileId: raw, profileName: username, textures });
  return Response.json({
    id: raw,
    name: username,
    properties: [{ name: "textures", value: Buffer.from(decoded).toString("base64") }],
  });
}

Bun.serve({
  port,
  hostname: "127.0.0.1",
  idleTimeout: 120,
  async fetch(req) {
    const path = new URL(req.url).pathname;
    if (path.startsWith("/session/minecraft/profile/")) {
      profileRequests++;
      if (latencyMs > 0) {
        await Bun.sleep(latencyMs);
      }
      if (errorRate > 0 && Math.random() < errorRate) {
        errors++;
        return Response.json({ error: "TooManyRequestsException" }, { status: 429 });
      }
      const raw = path.slice(path.lastIndexOf("/") + 1).replaceAll("-", "").toLowerCase();
      return raw.length === 32 ? profile(raw) : Response.json({}, { status: 404 });
    }
    if (path === "/profiles/minecraft" && req.method === "POST") {
      bulkNameRequests++;
      const names: string[] = await req.json();
      const claimed = names.flatMap((name) => {
        const h = md5(`name:${name.toLowerCase()}`);
        return roll(h, 0, nameClaimRate) ? [{ id: md5(h).slice(0, 32), name }] : [];
      });
      return Response.json(claimed);
    }
    if (path.startsWith("/texture/")) {
      return new Response(skinPng, { headers: { "Content-Type": "image/png" } });
    }
    if (path === "/blockedservers") {
      return new Response("");
    }
    if (path === "/stats") {
      return Response.json({ profileRequests, bulkNameRequests, errors });
    }
    return new Response("not found", { status: 404 });
  },
});
console.log(`Mojang stub on :${port} (skins=${skins} latency=${latencyMs}ms rename=${renameRate} skin=${skinRate} cape=${capeRate} error=${errorRate})`);
