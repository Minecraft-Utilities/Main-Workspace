import { isNotFoundError, mcUtilsCachedApi } from "@/common/mc-utils";
import { absoluteUrl, breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import { isProduction } from "@/common/utils";
import { JsonLd } from "@/components/json-ld";
import { PageShell } from "@/components/page-shell";
import PlayerCapes from "@/components/player/player-capes";
import PlayerHeader from "@/components/player/player-header";
import PlayerUsernameHistory from "@/components/player/player-username-history";
import PlayerViewsCounter from "@/components/player/player-view-counter";
import PlayerSkin from "@/components/player/skin/player-skin";
import PlayerSkins from "@/components/player/skin/player-skins";
import { SelectedCapeProvider } from "@/components/provider/selected-cape-provider";
import { SelectedSkinProvider } from "@/components/provider/selected-skin-provider";
import { Button } from "@/components/ui/button";
import { StateBlock } from "@/components/ui/state-block";
import { Metadata } from "next";
import Link from "next/link";
import { cache } from "react";

export const revalidate = 300;

const getPlayer = cache(async (query: string) => {
  const { data: player, error } = await mcUtilsCachedApi.player().fetch(query, "FULL");
  return { player, error };
});

function isUnknownPlayer(error: Awaited<ReturnType<typeof getPlayer>>["error"]) {
  return isNotFoundError(error) || error?.code === 400;
}

export async function generateMetadata(props: PageProps<"/player/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { player, error } = await getPlayer(query);

  if (!player) {
    return buildMetadata({
      title: isUnknownPlayer(error) ? `${decodeURIComponent(query)}: Player Not Found` : "Player unavailable",
      description: isUnknownPlayer(error)
        ? "No Minecraft player matches this username or UUID. Search for another player on MC Utils."
        : "This Minecraft player profile is temporarily unavailable. Try again in a moment.",
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
  const name = decodeURIComponent(query);

  if (!player) {
    const unknown = isUnknownPlayer(error);

    return (
      <PageShell>
        <StateBlock
          variant={unknown ? "not-found" : "error"}
          title={
            unknown ? (
              <>
                No player called <b className="font-semibold">{name}</b>
              </>
            ) : (
              "Player unavailable"
            )
          }
          description={
            unknown
              ? "Check the spelling, or paste the player's UUID instead."
              : "This profile could not be loaded. Try again in a moment."
          }
          action={
            <Button asChild variant="secondary">
              <Link href="/">Look up another player</Link>
            </Button>
          }
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

          <SelectedCapeProvider initialCape={player.cape ?? null}>
            <SelectedSkinProvider key={player.skin.textureId} initialSkin={player.skin}>
              <div className="flex w-full min-w-0 flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
                <aside className="flex w-full flex-col gap-4 lg:w-90 lg:shrink-0">
                  <PlayerSkin player={player} />
                </aside>

                <div className="flex min-w-0 flex-1 flex-col gap-4">
                  <PlayerUsernameHistory player={player} />
                  <PlayerSkins player={player} />
                  <PlayerCapes player={player} />
                </div>
              </div>
            </SelectedSkinProvider>
          </SelectedCapeProvider>
        </div>
      </PageShell>
    </>
  );
}
