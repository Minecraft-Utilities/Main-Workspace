import { env } from "@/common/env";
import { mcUtilsApi } from "@/common/mc-utils";
import { SITE_DESCRIPTION } from "@/common/site";
import LandingQuerySearch from "@/components/landing/landing-query-search";
import Statistics from "@/components/landing/statistics";
import ToolCards from "@/components/landing/tool-cards";
import { MEDIA_GRID, MediaCard } from "@/components/media-card";
import { PageShell, PageTitle } from "@/components/page-shell";
import { Button } from "@/components/ui/button";
import { ArrowRightIcon } from "lucide-react";
import { imageUrl } from "mcutils-js-api";
import type { Metadata } from "next";
import Link from "next/link";
import Script from "next/script";

export const dynamic = "force-dynamic";

const SITE_TITLE = "MC Utils: Minecraft Player & Server Lookup";

export const metadata: Metadata = {
  title: { absolute: SITE_TITLE },
  description: SITE_DESCRIPTION,
  openGraph: {
    url: env.NEXT_PUBLIC_BASE_URL,
    title: SITE_TITLE,
    description: SITE_DESCRIPTION,
  },
};

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "MC Utils",
  url: env.NEXT_PUBLIC_BASE_URL,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "All",
  description: SITE_DESCRIPTION,
  offers: {
    "@type": "Offer",
    price: "0",
    priceCurrency: "USD",
  },
};

export default async function HomePage() {
  const { data: trendingSkins } = await mcUtilsApi.skin().list(1, "TRENDING");

  return (
    <>
      <Script
        id="homepage-jsonld"
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <PageShell>
        <PageTitle className="text-center">Minecraft Player & Server Lookup</PageTitle>

        <div className="flex w-full flex-col items-center gap-5">
          <LandingQuerySearch />
          <Statistics />
        </div>

        <ToolCards />

        {trendingSkins && trendingSkins.items.length > 0 && (
          <div className="flex w-full flex-col gap-4">
            <div className="flex items-center justify-between">
              <h2 className="text-foreground text-xl font-bold tracking-tight">Trending Skins</h2>
              <Button variant="secondary" asChild>
                <Link href="/skins/trending">
                  View All
                  <ArrowRightIcon className="size-4" />
                </Link>
              </Button>
            </div>
            <div className={MEDIA_GRID}>
              {trendingSkins.items.slice(0, 10).map(skin => (
                <MediaCard
                  key={skin.id}
                  href={`/skin/${skin.id}`}
                  imageUrl={imageUrl(skin.parts.FULLBODY_ISO_FRONT, { size: 384 })}
                  alt="Minecraft Skin"
                  count={skin.uniqueOwners}
                />
              ))}
            </div>
          </div>
        )}

        <section className="text-muted-foreground max-w-2xl text-center text-sm leading-relaxed">
          <p>
            MC Utils is a free Minecraft lookup tool. Search any player to view their current skin, cape,
            UUID, and name history. Paste a Java or Bedrock server address to instantly check whether
            it&apos;s online, see its MOTD, and monitor live player counts: no login required.
          </p>
        </section>
      </PageShell>
    </>
  );
}
