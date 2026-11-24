import createClient, { type Client } from "openapi-fetch";
import type { paths } from "./generated/schema";
import type { ErrorResponse, Result } from "./types";

export type Http = {
  client: Client<paths>;
  baseUrl: string;
};

/**
 * Creates the typed HTTP client for an API endpoint.
 *
 * @param baseUrl the API endpoint, with or without a trailing slash
 * @param init extra `fetch` options applied to every request
 * @returns the client and the normalized base URL
 */
export function createHttp(baseUrl: string, init?: RequestInit): Http {
  const normalized = baseUrl.replace(/\/+$/, "");
  return { client: createClient<paths>({ baseUrl: normalized, ...init }), baseUrl: normalized };
}

/**
 * Collapses an openapi-fetch response into a {@link Result}. Network and parse failures are
 * reported as an error result too, so callers only ever handle one failure shape.
 */
export async function toResult<T>(
  request: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<Result<T>> {
  try {
    const { data, error, response } = await request;
    if (data !== undefined && !error) {
      return { data };
    }
    return { error: asErrorResponse(error, response) };
  } catch (cause) {
    return {
      error: {
        status: "500 INTERNAL_SERVER_ERROR",
        code: 0,
        message: cause instanceof Error ? cause.message : "Request failed",
        timestamp: new Date().toISOString(),
      },
    };
  }
}

function asErrorResponse(error: unknown, response: Response): ErrorResponse {
  if (typeof error === "object" && error !== null && "message" in error) {
    return error as ErrorResponse;
  }
  return {
    status: "500 INTERNAL_SERVER_ERROR",
    code: response.status,
    message: response.statusText || "Request failed",
    timestamp: new Date().toISOString(),
  };
}

/**
 * Builds a query string, skipping undefined values.
 *
 * @param params the query parameters
 * @returns the query string including the leading `?`, or an empty string when there are none
 */
export function queryString(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) {
      search.set(key, String(value));
    }
  }
  const str = search.toString();
  return str ? `?${str}` : "";
}
