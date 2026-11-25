import { env } from "@/common/env";
import type { Metadata } from "next";

const BASE_URL = env.NEXT_PUBLIC_BASE_URL.replace(/\/$/, "");
const DEFAULT_IMAGE = `${BASE_URL}/media/logo/logo.png`;

export interface BuildMetadataOptions {
  title: string;
  description: string;
  path: string;
  image?: string;
  noindex?: boolean;
}

export interface BreadcrumbItem {
  name: string;
  path: string;
}

/**
 * Resolves a path against the configured site origin.
 */
export function absoluteUrl(path: string): string {
  return new URL(path, BASE_URL).toString();
}

/**
 * Builds page metadata with a self-referencing canonical URL and matching Open Graph and
 * Twitter tags.
 *
 * @param options the page title, description, route path, optional image and indexability
 * @returns the metadata for the page
 */
export function buildMetadata({ title, description, path, image, noindex }: BuildMetadataOptions): Metadata {
  const images = [{ url: image ?? DEFAULT_IMAGE, alt: title }];

  return {
    title,
    description,
    alternates: { canonical: path },
    openGraph: {
      title,
      description,
      url: path,
      type: "website",
      locale: "en_US",
      images,
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images,
    },
    ...(noindex ? { robots: { index: false, follow: true } } : {}),
  };
}

/**
 * Builds a BreadcrumbList document for a detail page.
 *
 * @param items the breadcrumb trail, ordered from the site root to the current page
 * @returns the JSON-LD document
 */
export function breadcrumbJsonLd(items: BreadcrumbItem[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((item, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: item.name,
      item: absoluteUrl(item.path),
    })),
  };
}
