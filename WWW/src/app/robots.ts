import { env } from "@/common/env";
import type { MetadataRoute } from "next";

const BASE_URL = env.NEXT_PUBLIC_BASE_URL.replace(/\/$/, "");

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
    },
    sitemap: `${BASE_URL}/sitemap.xml`,
  };
}
