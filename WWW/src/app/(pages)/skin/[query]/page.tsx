import { env } from "@/common/env";
import { mcUtilsApi } from "@/common/mc-utils";
import { BackLink } from "@/components/breadcrumb-link";
import EntityDetails from "@/components/entity/entity-details";
import EntityPlayers from "@/components/entity/entity-players";
import { ErrorCard } from "@/components/error-card";
import { PageShell, PageTitle } from "@/components/page-shell";
import SimpleLink from "@/components/simple-link";
import SkinHeadCommands from "@/components/skin/skin-head-commands";
import SkinPreview from "@/components/skin/skin-preview";
import type { Metadata } from "next";
import Link from "next/link";
import Script from "next/script";
import { cache } from "react";

export const dynamic = "force-dynamic";

const getSkin = cache(async (query: string) => {
  const { data: skin } = await mcUtilsApi.skin().fetch(query);
  return skin ?? null;
});

const NOT_FOUND_DESCRIPTION =
  "This Minecraft skin could not be found. Browse all Minecraft skins on MC Utils.";

export async function generateMetadata(props: PageProps<"/skin/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const skin = await getSkin(query);

  if (!skin) {
    return {
      title: "Skin not found",
      description: NOT_FOUND_DESCRIPTION,
      openGraph: {
        title: "Skin not found",
        description: NOT_FOUND_DESCRIPTION,
      },
    };
  }

  const title = skin.firstSeenUsing ? `${skin.firstSeenUsing}'s Minecraft Skin` : "Minecraft Skin";
  const description = skin.firstSeenUsing
    ? `View ${skin.firstSeenUsing}'s Minecraft skin on MC Utils: preview the full body, download the texture, and see its users.`
    : "View this Minecraft skin on MC Utils: preview the full body, download the texture, and see its users.";

  return {
    title,
    description,
    icons: {
      icon: skin.parts.HEAD_ISO,
    },
    openGraph: {
      title,
      description,
      url: `${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}`,
      images: [
        {
          url: skin.parts.FULLBODY_ISO_FRONT,
        },
      ],
    },
  };
}

export default async function SkinsPage({ params }: PageProps<"/skin/[query]">) {
  const { query } = await params;
  const skin = await getSkin(query);

  if (!skin) {
    return (
      <PageShell>
        <BackLink href="/skins" label="Back to skins" />
        <ErrorCard
          title="Skin not found"
          message={
            <>
              We couldn&apos;t find a skin matching &quot;{query}&quot;.{" "}
              <Link href="/skins" className="underline underline-offset-4">
                Browse all skins
              </Link>
              .
            </>
          }
        />
      </PageShell>
    );
  }

  const skinName = skin.firstSeenUsing ? `${skin.firstSeenUsing}'s Skin` : "Minecraft Skin";
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "CreativeWork",
    name: skinName,
    image: skin.parts.FULLBODY_ISO_FRONT,
    url: `${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}`,
    description: `Minecraft skin${skin.firstSeenUsing ? ` worn by ${skin.firstSeenUsing}` : ""}.`,
  };

  return (
    <PageShell>
      <Script
        id="skin-jsonld"
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <div className="flex w-full flex-col gap-2">
        <BackLink href="/skins" label="Back to skins" />
        <PageTitle
          description={
            skin.firstSeenUsing ? (
              <SimpleLink
                href={`/player/${skin.firstSeenUsing}`}
                className="underline-offset-4 hover:underline"
              >
                View {skin.firstSeenUsing}&apos;s player profile
              </SimpleLink>
            ) : undefined
          }
        >
          {skinName}
        </PageTitle>
      </div>

      <div className="flex w-full flex-col gap-4 lg:flex-row">
        {/* Left: preview + details */}
        <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
          <SkinPreview skin={skin} />
          <EntityDetails kind="skin" entity={skin} />
        </div>

        {/* Right: users + commands */}
        <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
          <EntityPlayers
            kind="skin"
            accounts={skin.accountsSeenUsing ?? []}
            currentOwners={skin.currentOwners ?? 0}
          />
          <SkinHeadCommands skin={skin} />
        </div>
      </div>
    </PageShell>
  );
}
