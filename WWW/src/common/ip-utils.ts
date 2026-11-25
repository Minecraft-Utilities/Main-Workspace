/** Checked in order; Cloudflare's header is set by the edge so it wins over the spoofable chain. */
const IP_HEADERS = ["cf-connecting-ip", "x-forwarded-for"];

/** Loopback addresses say nothing useful about the visitor. */
export function isLoopback(ip: string): boolean {
  const address = ip.toLowerCase();
  return address === "::1" || address.startsWith("127.") || address.startsWith("::ffff:127.");
}

/** Returns the visitor's IP from the proxy headers, or undefined when absent or loopback. */
export function getRealIp(headers: Headers): string | undefined {
  for (const name of IP_HEADERS) {
    // x-forwarded-for lists the leftmost (client) address first.
    const ip = headers.get(name)?.split(",")[0]?.trim();
    if (ip) {
      return isLoopback(ip) ? undefined : ip;
    }
  }
  return undefined;
}
