import { McUtils } from "mcutils-js-api";
import { env } from "./env";

export const mcUtilsApi = new McUtils({ endpoint: env.NEXT_PUBLIC_API_URL });

const CAPE_PREVIEW_SKIN_ID = "1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b";

export function capeRenderUrl(capeId: string | number, size?: number): string {
  return mcUtilsApi.player().skinUrl(CAPE_PREVIEW_SKIN_ID, "FULLBODY_ISO_BACK", {
    capeId: String(capeId),
    size,
  });
}
