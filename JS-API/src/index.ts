import { BedrockApi } from "./api/bedrock";
import { CapeApi } from "./api/cape";
import { PlayerApi } from "./api/player";
import { ServerApi } from "./api/server";
import { SkinApi } from "./api/skin";
import { createHttp, toResult } from "./http";
import type { IpLookup, Result, Statistics } from "./types";

export { imageUrl } from "./image";
export * from "./types";

export type McUtilsOptions = {
  /**
   * The API endpoint (default: https://mc.fascinated.cc/api).
   */
  endpoint?: string;
  /**
   * Extra `fetch` options applied to every request (headers, cache, next, ...).
   */
  fetchOptions?: RequestInit;
};

/**
 * Entry point of the client. Each area of the API is a method returning its endpoints.
 */
export class McUtils {
  private readonly players: PlayerApi;
  private readonly skins: SkinApi;
  private readonly capes: CapeApi;
  private readonly servers: ServerApi;
  private readonly bedrockPlayers: BedrockApi;
  private readonly http;

  constructor({ endpoint = "https://mc.fascinated.cc/api", fetchOptions }: McUtilsOptions = {}) {
    this.http = createHttp(endpoint, fetchOptions);
    this.players = new PlayerApi(this.http);
    this.skins = new SkinApi(this.http);
    this.capes = new CapeApi(this.http);
    this.servers = new ServerApi(this.http);
    this.bedrockPlayers = new BedrockApi(this.http);
  }

  /**
   * Player lookups, search and skin renders.
   */
  player(): PlayerApi {
    return this.players;
  }

  /**
   * The skin catalogue.
   */
  skin(): SkinApi {
    return this.skins;
  }

  /**
   * The cape catalogue and cape renders.
   */
  cape(): CapeApi {
    return this.capes;
  }

  /**
   * Server pings, registry search and server images.
   */
  server(): ServerApi {
    return this.servers;
  }

  /**
   * Bedrock player lookups and skin renders.
   */
  bedrock(): BedrockApi {
    return this.bedrockPlayers;
  }

  /**
   * Looks up the location and ASN of an IP address.
   *
   * @param ip the IP address to look up
   * @returns the lookup, or the error if one occurred
   */
  ipLookup(ip: string): Promise<Result<IpLookup>> {
    return toResult(this.http.client.GET("/ips/{query}", { params: { path: { query: ip } } }));
  }

  /**
   * Fetches how many players, skins and capes the API has seen.
   *
   * @returns the statistics, or the error if one occurred
   */
  statistics(): Promise<Result<Statistics>> {
    return toResult(this.http.client.GET("/statistics"));
  }
}

export default McUtils;
