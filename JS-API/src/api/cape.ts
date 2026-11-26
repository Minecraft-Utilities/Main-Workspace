import { type Http, queryString, toResult } from "../http";
import type { Cape, CapePart, ImageOptions, Result } from "../types";

export class CapeApi {
  constructor(private readonly http: Http) {}

  /**
   * Fetches every known cape, most owned first.
   *
   * @returns the capes, or the error if one occurred
   */
  list(): Promise<Result<Cape[]>> {
    return toResult(this.http.client.GET("/capes"));
  }

  /**
   * Fetches a cape, including the players currently owning it.
   *
   * @param id the numeric id of the cape
   * @returns the cape, or the error if one occurred
   */
  fetch(id: number | string): Promise<Result<Cape>> {
    return toResult(this.http.client.GET("/capes/{id}", { params: { path: { id: String(id) } } }));
  }

  /**
   * Fetches a raw cape texture.
   *
   * @param query a player UUID or username, or a cape texture id
   * @returns the PNG texture, or the error if one occurred
   */
  texture(query: string): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/capes/{query}/texture.png", {
        params: { path: { query } },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Renders a part of a cape.
   *
   * @param query a player UUID or username, or a cape texture id
   * @param part the part of the cape to render
   * @param options the size options for the render
   * @returns the PNG image, or the error if one occurred
   */
  part(query: string, part: CapePart, options: ImageOptions = {}): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/capes/{query}/{part}.png", {
        params: { path: { query, part }, query: options },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Builds the URL of a rendered cape part, for use in an `<img src>`.
   *
   * @param query a player UUID or username, or a cape texture id
   * @param part the part of the cape to render
   * @param options the size options for the render
   * @returns the absolute image URL
   */
  partUrl(query: string, part: CapePart, options: ImageOptions = {}): string {
    return `${this.http.baseUrl}/capes/${encodeURIComponent(query)}/${part}.png${queryString(options)}`;
  }
}
