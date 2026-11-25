import { env } from "@/common/env";
import { mcUtilsApi } from "@/common/mc-utils";
import type { SkinSort } from "mcutils-js-api";
import type { MetadataRoute } from "next";

export const revalidate = 3600;

const BASE_URL = env.NEXT_PUBLIC_BASE_URL.replace(/\/$/, "");
const SKIN_PAGES_PER_SORT = 4;
const MAX_CAPE_PAGES = 50;

const STATIC_ENTRIES: MetadataRoute.Sitemap = [
  { url: `${BASE_URL}/`, changeFrequency: "daily", priority: 1 },
  { url: `${BASE_URL}/skins`, changeFrequency: "daily", priority: 0.9 },
  { url: `${BASE_URL}/capes`, changeFrequency: "weekly", priority: 0.8 },
  { url: `${BASE_URL}/names`, changeFrequency: "hourly", priority: 0.7 },
  { url: `${BASE_URL}/ip`, changeFrequency: "monthly", priority: 0.5 },
];

async function collectCapes(): Promise<MetadataRoute.Sitemap> {
  const first = await mcUtilsApi.cape().list(1);
  if (first.error) {
    return [];
  }

  const capes = [...first.data.items];
  const pages = Math.min(first.data.totalPages, MAX_CAPE_PAGES);
  for (let page = 2; page <= pages; page++) {
    const next = await mcUtilsApi.cape().list(page);
    if (next.error) {
      break;
    }
    capes.push(...next.data.items);
  }

  return capes.map(cape => ({
    url: `${BASE_URL}/cape/${cape.id}`,
    lastModified: new Date(cape.firstSeen),
    changeFrequency: "monthly" as const,
    priority: 0.6,
  }));
}

async function collectSkins(sort: SkinSort): Promise<MetadataRoute.Sitemap> {
  const entries: MetadataRoute.Sitemap = [];

  for (let page = 1; page <= SKIN_PAGES_PER_SORT; page++) {
    const { data, error } = await mcUtilsApi.skin().list(page, sort);
    if (error) {
      break;
    }

    entries.push(
      ...data.items.map(skin => ({
        url: `${BASE_URL}/skin/${skin.id}`,
        lastModified: new Date(skin.firstSeen),
        changeFrequency: "weekly" as const,
        priority: 0.5,
      }))
    );

    if (page >= data.totalPages) {
      break;
    }
  }

  return entries;
}

async function collectPlayers(): Promise<MetadataRoute.Sitemap> {
  const { data, error } = await mcUtilsApi.player().nameChanges();
  if (error) {
    return [];
  }

  const seen = new Set<string>();
  const entries: MetadataRoute.Sitemap = [];

  for (const change of data) {
    if (seen.has(change.playerId)) {
      continue;
    }
    seen.add(change.playerId);
    entries.push({
      url: `${BASE_URL}/player/${change.playerId}`,
      lastModified: new Date(change.timestamp),
      changeFrequency: "daily",
      priority: 0.6,
    });
  }

  return entries;
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const [capes, trendingSkins, latestSkins, players] = await Promise.all([
    collectCapes(),
    collectSkins("TRENDING"),
    collectSkins("LATEST"),
    collectPlayers(),
  ]);

  const seen = new Set<string>();
  return [...STATIC_ENTRIES, ...capes, ...trendingSkins, ...latestSkins, ...players].filter(entry => {
    if (seen.has(entry.url)) {
      return false;
    }
    seen.add(entry.url);
    return true;
  });
}
