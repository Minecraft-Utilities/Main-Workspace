import { type Http, queryString, toResult } from "../http";
import type { BedrockPlayer, BedrockSkinImageOptions, Result, SkinPart } from "../types";

export class BedrockApi {
  constructor(private readonly http: Http) {}

  /**
   * Fetches a Bedrock player. Only players Geyser has seen join a server can be found.
   *
   * @param id the gamertag or Floodgate UUID of the player
   * @returns the player, or the error if one occurred
   */
  player(id: string): Promise<Result<BedrockPlayer>> {
    return toResult(this.http.client.GET("/bedrock/players/{query}", { params: { path: { query: id } } }));
  }

  /**
   * Renders a part of a Bedrock player's skin.
   *
   * @param id the gamertag or Floodgate UUID of the player
   * @param part the part of the skin to render (default: `"FACE"`)
   * @param options the size and overlay options for the render
   * @returns the PNG image, or the error if one occurred
   */
  skin(id: string, part: SkinPart = "FACE", options: BedrockSkinImageOptions = {}): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/bedrock/players/{query}/skin/{type}.png", {
        params: { path: { query: id, type: part }, query: options },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Fetches a Bedrock player's raw skin texture.
   *
   * @param id the gamertag or Floodgate UUID of the player
   * @returns the PNG texture, or the error if one occurred
   */
  skinTexture(id: string): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/bedrock/players/{query}/skin/texture.png", {
        params: { path: { query: id } },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Builds the URL of a rendered Bedrock skin part, for use in an `<img src>`.
   *
   * @param id the gamertag or Floodgate UUID of the player
   * @param part the part of the skin to render (default: `"FACE"`)
   * @param options the size and overlay options for the render
   * @returns the absolute image URL
   */
  skinUrl(id: string, part: SkinPart = "FACE", options: BedrockSkinImageOptions = {}): string {
    return `${this.http.baseUrl}/bedrock/players/${encodeURIComponent(id)}/skin/${part.toLowerCase()}.png${queryString(options)}`;
  }
}
