import type { ErrorResponse } from "mcutils-js-api";
import { McUtils } from "mcutils-js-api";
import { env } from "./env";

export const mcUtilsApi = new McUtils({ endpoint: env.NEXT_PUBLIC_API_URL });

/**
 * Client whose API responses are cached for a revalidation window, for pages that would otherwise
 * hit the API on every request. Next.js keeps dynamic-param routes dynamic when they have no
 * static params, so caching the request is what bounds the upstream load.
 */
export const mcUtilsCachedApi = new McUtils({
  endpoint: env.NEXT_PUBLIC_API_URL,
  fetchOptions: { next: { revalidate: 300 } },
});

const CAPE_PREVIEW_SKIN_ID = "1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b";

/**
 * Checks whether an API error means the requested resource does not exist.
 *
 * @param error the error returned by the API client, if any
 * @returns true when the API answered 404
 */
export function isNotFoundError(error: ErrorResponse | undefined): boolean {
  return error?.code === 404 || error?.status === "404 NOT_FOUND";
}

export function capeRenderUrl(capeId: string | number, size?: number): string {
  return mcUtilsApi.player().skinUrl(CAPE_PREVIEW_SKIN_ID, "FULLBODY_ISO_BACK", {
    capeId: String(capeId),
    size,
  });
}
