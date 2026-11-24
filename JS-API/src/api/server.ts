import { type Http, queryString, toResult } from "../http";
import type { BedrockServer, ImageOptions, JavaServer, Platform, Result, ServerRegistryEntry } from "../types";

function address(host: string, port?: number): string {
  return port === undefined ? host : `${host}:${port}`;
}

export class ServerApi {
  constructor(private readonly http: Http) {}

  /**
   * Pings a Java server.
   *
   * @param host the hostname of the server (eg: aetheria.cc)
   * @param port the port of the server, resolved through DNS when omitted
   * @returns the server, or the error if one occurred
   */
  fetchJava(host: string, port?: number): Promise<Result<JavaServer>> {
    return toResult(
      this.http.client.GET("/servers/{platform}/{hostname}", {
        params: { path: { platform: "JAVA", hostname: address(host, port) } },
      }),
    ) as Promise<Result<JavaServer>>;
  }

  /**
   * Pings a Bedrock server.
   *
   * @param host the hostname of the server (eg: geo.hivebedrock.network)
   * @param port the port of the server, the default Bedrock port when omitted
   * @returns the server, or the error if one occurred
   */
  fetchBedrock(host: string, port?: number): Promise<Result<BedrockServer>> {
    return toResult(
      this.http.client.GET("/servers/{platform}/{hostname}", {
        params: { path: { platform: "BEDROCK", hostname: address(host, port) } },
      }),
    ) as Promise<Result<BedrockServer>>;
  }

  /**
   * Checks whether Mojang blocks a hostname.
   *
   * @param host the hostname to check
   * @returns whether the hostname is blocked, or the error if one occurred
   */
  async blocked(host: string): Promise<Result<boolean>> {
    const result = await toResult(
      this.http.client.GET("/servers/blocked/{hostname}", { params: { path: { hostname: host } } }),
    );
    return result.error ? result : { data: result.data.blocked };
  }

  /**
   * Searches the server registry.
   *
   * @param query the text to search for
   * @returns the matching registry entries, or the error if one occurred
   */
  search(query: string): Promise<Result<ServerRegistryEntry[]>> {
    return toResult(this.http.client.GET("/servers", { params: { query: { query } } }));
  }

  /**
   * Fetches a server's favicon.
   *
   * @param host the hostname of the server
   * @param port the port of the server
   * @returns the PNG icon, or the error if one occurred
   */
  icon(host: string, port?: number): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/servers/{hostname}/icon.png", {
        params: { path: { hostname: address(host, port) } },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Renders a server status preview.
   *
   * @param platform the platform of the server
   * @param host the hostname of the server
   * @param options the size of the render and the port of the server
   * @returns the PNG image, or the error if one occurred
   */
  preview(platform: Platform, host: string, options: ImageOptions & { port?: number } = {}): Promise<Result<ArrayBuffer>> {
    return toResult(
      this.http.client.GET("/servers/{platform}/{hostname}/preview.png", {
        params: { path: { platform, hostname: address(host, options.port) }, query: { size: options.size } },
        parseAs: "arrayBuffer",
      }),
    );
  }

  /**
   * Builds the URL of a server's favicon, for use in an `<img src>`.
   *
   * @param host the hostname of the server
   * @param port the port of the server
   * @returns the absolute image URL
   */
  iconUrl(host: string, port?: number): string {
    return `${this.http.baseUrl}/servers/${encodeURIComponent(address(host, port))}/icon.png`;
  }

  /**
   * Builds the URL of a server status preview, for use in an `<img src>`.
   *
   * @param platform the platform of the server
   * @param host the hostname of the server
   * @param options the size of the render and the port of the server
   * @returns the absolute image URL
   */
  previewUrl(platform: Platform, host: string, options: ImageOptions & { port?: number } = {}): string {
    const { port, ...query } = options;
    return `${this.http.baseUrl}/servers/${platform}/${encodeURIComponent(address(host, port))}/preview.png${queryString(query)}`;
  }
}
