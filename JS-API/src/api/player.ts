import { type Http, queryString, toResult } from "../http";
import type {
  BasicPlayer,
  FullPlayer,
  PlayerType,
  RecentUsernameChange,
  Result,
  SkinImageOptions,
  SkinPart,
} from "../types";

export class PlayerApi {
  constructor(private readonly http: Http) {}

  /**
   * Fetches a player.
   *
   * @param id the UUID or username of the player
   * @param type `"FULL"` also returns the skin, cape and username history (default: `"BASIC"`)
   * @returns the player, or the error if one occurred
   */
  fetch(id: string, type?: "BASIC"): Promise<Result<BasicPlayer>>;
  fetch(id: string, type: "FULL"): Promise<Result<FullPlayer>>;
  fetch(id: string, type: PlayerType = "BASIC"): Promise<Result<BasicPlayer | FullPlayer>> {
    return toResult(
      this.http.client.GET("/players/{id}", { params: { path: { id }, query: { type } } }),
    ) as Promise<Result<BasicPlayer | FullPlayer>>;
  }

  /**
   * Searches players by username prefix, case-insensitive.
   *
   * @param query the username prefix to search for
   * @param type `"FULL"` returns the full player data for every match (default: `"BASIC"`)
   * @returns the matching players, or the error if one occurred
   */
  search(query: string, type?: "BASIC"): Promise<Result<BasicPlayer[]>>;
  search(query: string, type: "FULL"): Promise<Result<FullPlayer[]>>;
  search(query: string, type: PlayerType = "BASIC"): Promise<Result<BasicPlayer[] | FullPlayer[]>> {
    return toResult(
      this.http.client.GET("/players", { params: { query: { query, type } } }),
    ) as Promise<Result<BasicPlayer[] | FullPlayer[]>>;
  }

  /**
   * Fetches the most recent username changes seen across all players.
   *
   * @returns the recent username changes, or the error if one occurred
   */
  nameChanges(): Promise<Result<RecentUsernameChange[]>> {
    return toResult(this.http.client.GET("/players/name-changes"));
  }

  /**
   * Renders a part of a player's skin.
   *
   * @param id the UUID or username of the player
   * @param part the part of the skin to render (default: `"FACE"`)
   * @param options the size, overlay and cape options for the render
   * @returns the PNG image, or the error if one occurred
   */
  skin(id: string, part: SkinPart = "FACE", options: SkinImageOptions = {}): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/skins/{query}/{type}.png", {
        params: { path: { query: id, type: part }, query: options },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Fetches a player's raw skin texture.
   *
   * @param id the UUID or username of the player
   * @returns the PNG texture, or the error if one occurred
   */
  skinTexture(id: string): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/skins/{query}/texture.png", {
        params: { path: { query: id } },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Builds the URL of a rendered skin part, for use in an `<img src>`.
   *
   * @param id the UUID or username of the player
   * @param part the part of the skin to render (default: `"FACE"`)
   * @param options the size, overlay and cape options for the render
   * @returns the absolute image URL
   */
  skinUrl(id: string, part: SkinPart = "FACE", options: SkinImageOptions = {}): string {
    return `${this.http.baseUrl}/skins/${encodeURIComponent(id)}/${part}.png${queryString(options)}`;
  }
}
