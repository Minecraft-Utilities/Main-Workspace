import { isNotFoundError, mcUtilsCachedApi } from "@/common/mc-utils";
import { absoluteUrl, breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import { isProduction } from "@/common/utils";
import { ErrorCard } from "@/components/error-card";
import { JsonLd } from "@/components/json-ld";
import { PageShell } from "@/components/page-shell";
import PlayerCapes from "@/components/player/player-capes";
import PlayerDetails from "@/components/player/player-details";
import PlayerHeader from "@/components/player/player-header";
import PlayerUsernameHistory from "@/components/player/player-username-history";
import PlayerViewsCounter from "@/components/player/player-view-counter";
import PlayerSkin from "@/components/player/skin/player-skin";
import PlayerSkins from "@/components/player/skin/player-skins";
import { SelectedCapeProvider } from "@/components/provider/selected-cape-provider";
import { SelectedSkinProvider } from "@/components/provider/selected-skin-provider";
import { Metadata } from "next";
import { notFound } from "next/navigation";
import { cache } from "react";

export const revalidate = 300;

const getPlayer = cache(async (query: string) => {
  const { data: player, error } = await mcUtilsCachedApi.player().fetch(query, "FULL");
  return { player, error };
});

export async function generateMetadata(props: PageProps<"/player/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { player, error } = await getPlayer(query);

  if (!player) {
    // The player endpoint answers 400 when the query is neither a UUID nor a username.
    if (isNotFoundError(error) || error?.code === 400) {
      notFound();
    }
    return buildMetadata({
      title: "Player unavailable",
      description: "This Minecraft player profile is temporarily unavailable. Try again in a moment.",
      path: `/player/${query}`,
      noindex: true,
    });
  }

  const title = `${player.username}: Minecraft Profile, Skin & UUID`;
  const description = `View ${player.username}'s Minecraft skin, cape, UUID, and profile history on MC Utils.`;

  return {
    ...buildMetadata({
      title,
      description,
      path: `/player/${player.uniqueId}`,
      image: player.skin.parts.FULLBODY_ISO_FRONT,
    }),
    icons: {
      icon: mcUtilsCachedApi.player().skinUrl(player.uniqueId, "HEAD_ISO", { size: 64 }),
    },
  };
}

export default async function PlayerPage({ params }: PageProps<"/player/[query]">) {
  const { query } = await params;
  const { player, error } = await getPlayer(query);

  if (!player) {
    if (isNotFoundError(error) || error?.code === 400) {
      notFound();
    }
    return (
      <PageShell>
        <ErrorCard
          title="Player unavailable"
          message={error?.message ?? "This Minecraft player could not be loaded. Try again in a moment."}
        />
      </PageShell>
    );
  }

  const jsonLd = [
    {
      "@context": "https://schema.org",
      "@type": "ProfilePage",
      url: absoluteUrl(`/player/${player.uniqueId}`),
      mainEntity: {
        "@type": "Person",
        name: player.username,
        identifier: player.uniqueId,
        image: mcUtilsCachedApi.player().skinUrl(player.uniqueId, "HEAD_ISO", { size: 256 }),
      },
    },
    breadcrumbJsonLd([
      { name: "Home", path: "/" },
      { name: player.username, path: `/player/${player.uniqueId}` },
    ]),
  ];

  return (
    <>
      <JsonLd id="player-jsonld" data={jsonLd} />
      {isProduction() && <PlayerViewsCounter playerQuery={player.uniqueId} />}
      <PageShell>
        <div className="flex w-full min-w-0 flex-col gap-10">
          <PlayerHeader player={player} />

          <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
            <section className="flex w-full flex-col gap-4 lg:w-90 lg:shrink-0">
              <SelectedCapeProvider initialCape={player.cape ?? null}>
                <SelectedSkinProvider key={player.skin.textureId} initialSkin={player.skin}>
                  <PlayerSkin player={player} />
                  <PlayerSkins player={player} />
                </SelectedSkinProvider>
                <PlayerCapes player={player} />
              </SelectedCapeProvider>
            </section>

            <aside className="flex min-w-0 flex-1 flex-col gap-4">
              <PlayerDetails player={player} />
              <PlayerUsernameHistory player={player} />
            </aside>
          </div>
        </div>
      </PageShell>
    </>
  );
}
