import { imageUrl } from "mcutils-js-api";

/**
 * Encode the skin textures into the base64 JSON that Minecraft expects as a profile texture value
 *
 * @param textureId the texture id of the skin
 * @returns the base64 string
 */
export function encodeSkinTextures(textureId: string) {
  return btoa(
    JSON.stringify({ textures: { SKIN: { url: `http://textures.minecraft.net/texture/${textureId}` } } })
  );
}

/**
 * Applies the size and optional cape to a rendered skin part URL.
 */
export function skinPartUrl(partUrl: string, capeId?: string | number | null, size?: number): string {
  return imageUrl(partUrl, {
    size,
    capeId: capeId === undefined || capeId === null || capeId === "" ? undefined : String(capeId),
  });
}
