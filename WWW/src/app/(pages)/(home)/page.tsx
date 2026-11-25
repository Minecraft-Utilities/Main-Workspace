import { mcUtilsApi } from "@/common/mc-utils";
import { absoluteUrl, buildMetadata } from "@/common/seo";
import { SITE_DESCRIPTION } from "@/common/site";
import { JsonLd } from "@/components/json-ld";
import LandingQuerySearch from "@/components/landing/landing-query-search";
import Statistics from "@/components/landing/statistics";
import ToolCards from "@/components/landing/tool-cards";
import { MediaCard } from "@/components/media-card";
import { PageShell } from "@/components/page-shell";
import { Button } from "@/components/ui/button";
import { MediaGrid } from "@/components/ui/media-grid";
import { ArrowRightIcon } from "lucide-react";
import { imageUrl } from "mcutils-js-api";
import type { Metadata } from "next";
import Link from "next/link";

export const dynamic = "force-dynamic";

const SITE_TITLE = "MC Utils: Minecraft Player & Server Lookup";

const homeMetadata = buildMetadata({
  title: SITE_TITLE,
  description: SITE_DESCRIPTION,
  path: "/",
});

export const metadata: Metadata = {
  ...homeMetadata,
  title: { absolute: SITE_TITLE },
};

const jsonLd = [
  {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: "MC Utils",
    url: absoluteUrl("/"),
    potentialAction: {
      "@type": "SearchAction",
      target: {
        "@type": "EntryPoint",
        urlTemplate: `${absoluteUrl("/player/")}{search_term_string}`,
      },
      "query-input": "required name=search_term_string",
    },
  },
  {
    "@context": "https://schema.org",
    "@type": "WebApplication",
    name: "MC Utils",
    url: absoluteUrl("/"),
    applicationCategory: "UtilitiesApplication",
    operatingSystem: "All",
    description: SITE_DESCRIPTION,
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "USD",
    },
  },
];

export default async function HomePage() {
  const { data: trendingSkins } = await mcUtilsApi.skin().list(1, "TRENDING");

  return (
    <>
      <JsonLd id="homepage-jsonld" data={jsonLd} />
      <PageShell>
        <header className="flex w-full flex-col items-center gap-6 text-center">
          <h1 className="text-foreground max-w-[20ch] text-[28px] leading-[34px] font-[650] tracking-[-0.03em] sm:text-[32px] sm:leading-[38px] md:text-[42px] md:leading-[48px]">
            Minecraft Player &amp; Server Lookup
          </h1>
          <LandingQuerySearch />
        </header>

        <Statistics />

        <ToolCards />

        {trendingSkins && trendingSkins.items.length > 0 && (
          <section className="flex w-full flex-col gap-4">
            <div className="flex items-center justify-between gap-4">
              <h2 className="text-foreground text-lg leading-7 font-semibold tracking-[-0.01em] whitespace-nowrap sm:text-xl">
                Trending Skins
              </h2>
              <Button variant="secondary" asChild>
                <Link href="/skins/trending">
                  View all
                  <ArrowRightIcon className="size-4" aria-hidden />
                </Link>
              </Button>
            </div>
            <MediaGrid>
              {trendingSkins.items.slice(0, 10).map(skin => (
                <MediaCard
                  key={skin.id}
                  href={`/skin/${skin.id}`}
                  imageUrl={imageUrl(skin.parts.FULLBODY_ISO_FRONT, { size: 384 })}
                  alt={`Minecraft skin ${skin.id}`}
                  count={skin.uniqueOwners}
                />
              ))}
            </MediaGrid>
          </section>
        )}

        <p className="text-text-secondary w-full max-w-[65ch] text-left text-sm leading-relaxed">
          MC Utils is a free Minecraft lookup tool. Search any player to view their current skin, cape,
          UUID and name history. Paste a Java or Bedrock server address to check whether it&apos;s online,
          see its MOTD and watch live player counts. No login required.
        </p>
      </PageShell>
    </>
  );
}
