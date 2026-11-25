/**
 * Encode the skin textures into the base64 JSON that Minecraft expects as a profile texture value
 *
 * @param textureId the texture id of the skin
 * @returns the base64 string
 */
export function encodeSkinTextures(textureId: string) {
  return btoa(JSON.stringify({ textures: { SKIN: { url: `http://textures.minecraft.net/texture/${textureId}` } } }));
}

/**
 * Adds a cape to a rendered skin part URL, leaving the URL untouched when no cape is selected.
 */
export function skinPartUrl(partUrl: string, capeId?: string | number | null): string {
  if (capeId === undefined || capeId === null || capeId === "") {
    return partUrl;
  }
  return `${partUrl}${partUrl.includes("?") ? "&" : "?"}capeId=${encodeURIComponent(String(capeId))}`;
}
