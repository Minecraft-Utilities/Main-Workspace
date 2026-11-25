import type { ErrorResponse } from "mcutils-js-api";

const GENERIC_MESSAGE = "Something went wrong on our side. Try again in a moment.";

const NOT_FOUND_MESSAGE = "We couldn't find that. Check the spelling or try a different search.";
const BAD_REQUEST_MESSAGE = "That request didn't look right. Check the details and try again.";
const SERVER_ERROR_MESSAGE = "Something went wrong on our side. Try again in a moment.";
const UNAVAILABLE_MESSAGE = "The lookup service isn't answering right now. Try again in a minute.";
const VALIDATION_MESSAGE = "Some of those details weren't valid. Check them and try again.";
const UUID_MESSAGE = "That doesn't look like a valid UUID. Check it and try again.";
const BLOCKED_ADDRESS_MESSAGE = "That address can't be reached from here. Try a public server address.";
const DNS_MESSAGE = "We couldn't find that address. Check the spelling and try again.";
const TIMEOUT_MESSAGE = "That server didn't respond. It may be offline right now.";

const STATUS_MESSAGES: Readonly<Record<number, string>> = {
  400: BAD_REQUEST_MESSAGE,
  401: "You're not allowed to do that. Try a different search.",
  403: "You're not allowed to do that. Try a different search.",
  404: NOT_FOUND_MESSAGE,
  405: "That action isn't supported here.",
  408: "That took too long to answer. Try again.",
  409: "That request clashed with something else. Try again.",
  410: NOT_FOUND_MESSAGE,
  413: "That input is too long. Shorten it and try again.",
  414: "That input is too long. Shorten it and try again.",
  415: "We couldn't read that request. Check the details and try again.",
  422: VALIDATION_MESSAGE,
  429: "Too many requests. Wait a moment and try again.",
  451: "That isn't available from here.",
  500: SERVER_ERROR_MESSAGE,
  501: "That isn't available yet. Try again later.",
  502: UNAVAILABLE_MESSAGE,
  503: UNAVAILABLE_MESSAGE,
  504: UNAVAILABLE_MESSAGE,
};

const RAW_MESSAGE_PATTERNS: ReadonlyArray<readonly [RegExp, string]> = [
  [/invalid uuid/i, UUID_MESSAGE],
  [/blacklisted/i, BLOCKED_ADDRESS_MESSAGE],
  [/unknown host|no such host|could not resolve|couldn't resolve|failed to resolve|dns/i, DNS_MESSAGE],
  [/connection refused|no route to host|unreachable|timed out|timeout|connect timed out/i, TIMEOUT_MESSAGE],
  [/not found|does not exist/i, NOT_FOUND_MESSAGE],
  [/bad request|^(invalid|bad)\b/i, BAD_REQUEST_MESSAGE],
  [
    /internal server error|exception|nullpointer|illegalargument|illegalstate|stack trace|unexpected error/i,
    SERVER_ERROR_MESSAGE,
  ],
  [/failed to (fetch|load|connect)/i, UNAVAILABLE_MESSAGE],
];

const RAW_MESSAGE_LEAD = /^(invalid|bad|failed|error|cannot|unable|unknown|missing)\b/i;
const INTERNAL_MARKER = /\b(exception|nullpointer|illegalargument|illegalstate|stack trace)\b/i;

function statusMessage(status: number): string {
  return STATUS_MESSAGES[status] ?? (status >= 500 ? SERVER_ERROR_MESSAGE : GENERIC_MESSAGE);
}

function statusFromText(value: string): number | undefined {
  const match = /^(\d{3})(?:\s|$)/.exec(value);
  if (!match) {
    return undefined;
  }
  return Number(match[1]);
}

function rawMessage(value: string): string | undefined {
  const trimmed = value.trim();
  for (const [pattern, message] of RAW_MESSAGE_PATTERNS) {
    if (pattern.test(trimmed)) {
      return message;
    }
  }
  return undefined;
}

/**
 * Maps an API status or error to copy a person can act on.
 *
 * Backend text is never echoed: unknown codes, unknown messages and plain runtime errors all fall
 * back to generic copy.
 *
 * @param error the API error, HTTP status, status string or raw backend message
 * @returns human copy for the failure
 */
export function errorMessage(error: ErrorResponse | Error | number | string | null | undefined): string {
  if (error === null || error === undefined) {
    return GENERIC_MESSAGE;
  }

  if (typeof error === "number") {
    return statusMessage(error);
  }

  if (typeof error === "string") {
    const status = statusFromText(error.trim());
    if (status !== undefined) {
      return statusMessage(status);
    }
    return rawMessage(error) ?? GENERIC_MESSAGE;
  }

  if (error instanceof Error) {
    return GENERIC_MESSAGE;
  }

  const code = Number(error.code);
  if (Number.isFinite(code) && code > 0) {
    return statusMessage(code);
  }

  const status = statusFromText(String(error.status ?? ""));
  return status !== undefined ? statusMessage(status) : GENERIC_MESSAGE;
}

/**
 * Detects strings that came from the API rather than from our own copy, so a raw backend message
 * can be replaced before it is rendered.
 *
 * @param value the string to inspect
 * @returns true when the string is a status or a known backend error message
 */
export function isApiErrorMessage(value: string): boolean {
  const trimmed = value.trim();
  if (statusFromText(trimmed) !== undefined) {
    return true;
  }
  if (RAW_MESSAGE_LEAD.test(trimmed) || INTERNAL_MARKER.test(trimmed)) {
    return true;
  }
  return rawMessage(trimmed) !== undefined;
}
