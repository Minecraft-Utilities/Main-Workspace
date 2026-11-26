export const GAMERTAG_PATTERN = /^\.?[A-Za-z0-9][A-Za-z0-9 _]{0,15}$/;

/**
 * Floodgate derives Bedrock UUIDs by zeroing the first 16 hex digits, so a UUID with that prefix
 * can only belong to a Bedrock player and never collides with a Java account.
 */
const FLOODGATE_UUID_PATTERN = /^0{16}[0-9a-f]{16}$/i;

export function isBedrockQuery(query: string): boolean {
  const value = query.trim();
  if (value.startsWith(".")) {
    return true;
  }
  if (value.includes(" ")) {
    return true;
  }
  return FLOODGATE_UUID_PATTERN.test(value.replace(/-/g, ""));
}

export function bedrockPath(query: string): string {
  return `/bedrock/${encodeURIComponent(query.trim().replace(/^\./, ""))}`;
}
