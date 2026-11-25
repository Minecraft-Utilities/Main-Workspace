import { isNotFoundError, mcUtilsApi } from "@/common/mc-utils";
import { absoluteUrl, breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import { BackLink } from "@/components/breadcrumb-link";
import EntityPlayers from "@/components/entity/entity-players";
import { JsonLd } from "@/components/json-ld";
import { PageShell } from "@/components/page-shell";
import SkinHeadCommands from "@/components/skin/skin-head-commands";
import SkinHeader from "@/components/skin/skin-header";
import SkinPreview from "@/components/skin/skin-preview";
import { Button } from "@/components/ui/button";
import { StateBlock } from "@/components/ui/state-block";
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
        <StateBlock
          variant="error"
          title="Skin unavailable"
          description="This skin could not be loaded. Try again in a moment."
          action={
            <Button asChild variant="secondary">
              <Link href="/skins">Browse skins</Link>
            </Button>
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

      <div className="flex w-full min-w-0 flex-col gap-10">
        <div className="flex w-full min-w-0 flex-col gap-4">
          <BackLink href="/skins" label="Back to skins" />
          <SkinHeader skin={skin} />
        </div>

        <div className="flex w-full min-w-0 flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
          <aside className="flex w-full flex-col gap-4 lg:w-90 lg:shrink-0">
            <SkinPreview skin={skin} />
            <SkinHeadCommands skin={skin} />
          </aside>

          <div className="flex min-w-0 flex-1 flex-col gap-4">
            <EntityPlayers
              kind="skin"
              accounts={skin.accountsSeenUsing ?? []}
              currentOwners={skin.currentOwners ?? 0}
            />
          </div>
        </div>
      </div>
    </PageShell>
  );
}
