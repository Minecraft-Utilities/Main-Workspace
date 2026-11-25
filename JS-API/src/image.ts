import { queryString } from "./http";
import type { SkinImageOptions } from "./types";

/**
 * Applies render options to an image URL returned by the API, such as an entry of a skin's or cape's `parts`.
 *
 * @param url the image URL
 * @param options the size, overlay and cape options for the render
 * @returns the image URL with the options set, replacing any already present
 */
export function imageUrl(url: string, options: SkinImageOptions = {}): string {
  const target = new URL(url);
  const params = new URLSearchParams(queryString(options));
  for (const [key, value] of params) {
    target.searchParams.set(key, value);
  }
  return target.toString();
}
