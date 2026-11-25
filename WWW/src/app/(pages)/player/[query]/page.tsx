import { mcUtilsApi } from "@/common/mc-utils";
import { isProduction } from "@/common/utils";
import { ErrorCard } from "@/components/error-card";
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
import { cache } from "react";

export const dynamic = "force-dynamic";

const getPlayer = cache(async (query: string) => {
  const { data: player, error } = await mcUtilsApi.player().fetch(query, "FULL");
  return { player, error };
});

export async function generateMetadata(props: PageProps<"/player/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { player } = await getPlayer(query);

  if (player === undefined) {
    return {
      title: "Player not found",
      description: "This Minecraft player could not be found.",
      openGraph: {
        title: "Player not found",
        description: "This Minecraft player could not be found.",
      },
    };
  }
  const previewImage = player.skin.parts.FULLBODY_ISO_FRONT;
  const favicon = player.skin.parts.HEAD_ISO;
  return {
    title: `${player.username}: Minecraft Profile, Skin & UUID`,
    description: `View ${player.username}'s Minecraft skin, cape, UUID, and profile history on MC Utils.`,
    openGraph: {
      title: `${player.username}: Minecraft Profile, Skin & UUID`,
      description: `View ${player.username}'s Minecraft skin, cape, UUID, and profile history on MC Utils.`,
      images: [
        {
          url: previewImage,
        },
      ],
    },
    icons: {
      icon: favicon,
    },
  };
}

export default async function PlayerPage({ params }: PageProps<"/player/[query]">) {
  const { query } = await params;
  const { player, error } = await getPlayer(query);

  if (!player) {
    return (
      <PageShell>
        <ErrorCard
          title="Player not found"
          message={error?.message ?? "This Minecraft player could not be found."}
        />
      </PageShell>
    );
  }

  return (
    <PageShell>
      {isProduction() && <PlayerViewsCounter playerQuery={player.uniqueId} />}
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
  );
}
