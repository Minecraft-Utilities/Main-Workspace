import type { Http } from "../http";
import { toResult } from "../http";
import type { Result, Skin, SkinPage, SkinSort } from "../types";

export class SkinApi {
  constructor(private readonly http: Http) {}

  /**
   * Fetches a page of skins.
   *
   * @param page the page to fetch (default: 1)
   * @param sort the sort order (default: `"TRENDING"`)
   * @returns the page of skins, or the error if one occurred
   */
  list(page = 1, sort: SkinSort = "TRENDING"): Promise<Result<SkinPage>> {
    return toResult(this.http.client.GET("/skins", { params: { query: { page, sort } } }));
  }

  /**
   * Fetches a skin, including the players currently using it.
   *
   * @param id the numeric id of the skin
   * @returns the skin, or the error if one occurred
   */
  fetch(id: number | string): Promise<Result<Skin>> {
    return toResult(this.http.client.GET("/skins/{id}", { params: { path: { id: String(id) } } }));
  }
}
