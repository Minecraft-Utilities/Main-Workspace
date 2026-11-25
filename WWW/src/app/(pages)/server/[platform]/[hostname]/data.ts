import { mcUtilsApi } from "@/common/mc-utils";
import type { Platform } from "mcutils-js-api";
import { cache } from "react";

export const SERVER_BACKGROUND_FALLBACK = "/media/backgrounds/server.webp";

const fetchServerCached = cache((hostname: string, edition: Platform) =>
  edition === "JAVA" ? mcUtilsApi.server().fetchJava(hostname) : mcUtilsApi.server().fetchBedrock(hostname)
);

export async function getServer(platform: string, hostname: string) {
  const edition: Platform = platform.toLowerCase() === "bedrock" ? "BEDROCK" : "JAVA";

  const { data: server, error } = await fetchServerCached(decodeURIComponent(hostname), edition);
  return { server, error, edition };
}
