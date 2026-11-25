import { env } from "./env";

export function websocketUrl(path: string): string {
  const base = env.NEXT_PUBLIC_API_URL.replace(/^http/i, "ws").replace(/\/$/, "");
  return `${base}/${path.replace(/^\//, "")}`;
}
