import { isNotFoundError, mcUtilsApi } from "@/common/mc-utils";
import { absoluteUrl, breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import { BackLink } from "@/components/breadcrumb-link";
import EntityDetails from "@/components/entity/entity-details";
import EntityPlayers from "@/components/entity/entity-players";
import { ErrorCard } from "@/components/error-card";
import { JsonLd } from "@/components/json-ld";
import { PageShell, PageTitle } from "@/components/page-shell";
import SimpleLink from "@/components/simple-link";
import SkinHeadCommands from "@/components/skin/skin-head-commands";
import SkinPreview from "@/components/skin/skin-preview";
import { imageUrl } from "mcutils-js-api";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

export const dynamic = "force-dynamic";

const getSkin = cache(async (query: string) => {
  if (!/^\d+$/.test(query)) {
    notFound();
  }
  const { data: skin, error } = await mcUtilsApi.skin().fetch(query);
  return { skin, error };
});

export async function generateMetadata(props: PageProps<"/skin/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { skin, error } = await getSkin(query);

  if (!skin) {
    if (isNotFoundError(error)) {
      notFound();
    }
    return buildMetadata({
      title: "Skin unavailable",
      description: "This Minecraft skin is temporarily unavailable. Try again in a moment.",
      path: `/skin/${query}`,
      noindex: true,
    });
  }

  const title = skin.firstSeenUsing ? `${skin.firstSeenUsing}'s Minecraft Skin` : "Minecraft Skin";
  const description = skin.firstSeenUsing
    ? `View ${skin.firstSeenUsing}'s Minecraft skin on MC Utils: preview the full body, download the texture, and see its users.`
    : "View this Minecraft skin on MC Utils: preview the full body, download the texture, and see its users.";

  return {
    ...buildMetadata({
      title,
      description,
      path: `/skin/${skin.id}`,
      image: skin.parts.FULLBODY_ISO_FRONT,
    }),
    icons: {
      icon: imageUrl(skin.parts.HEAD_ISO, { size: 64 }),
    },
  };
}

export default async function SkinsPage({ params }: PageProps<"/skin/[query]">) {
  const { query } = await params;
  const { skin, error } = await getSkin(query);

  if (!skin) {
    if (isNotFoundError(error)) {
      notFound();
    }
    return (
      <PageShell>
        <BackLink href="/skins" label="Back to skins" />
        <ErrorCard
          title="Skin unavailable"
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
  const jsonLd = [
    {
      "@context": "https://schema.org",
      "@type": "CreativeWork",
      name: skinName,
      image: skin.parts.FULLBODY_ISO_FRONT,
      url: absoluteUrl(`/skin/${skin.id}`),
      description: `Minecraft skin${skin.firstSeenUsing ? ` worn by ${skin.firstSeenUsing}` : ""}.`,
    },
    breadcrumbJsonLd([
      { name: "Home", path: "/" },
      { name: "Minecraft Skins", path: "/skins" },
      { name: skinName, path: `/skin/${skin.id}` },
    ]),
  ];

  return (
    <PageShell>
      <JsonLd id="skin-jsonld" data={jsonLd} />

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
