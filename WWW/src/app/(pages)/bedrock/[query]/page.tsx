import { isNotFoundError, mcUtilsCachedApi } from "@/common/mc-utils";
import { absoluteUrl, breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import { JsonLd } from "@/components/json-ld";
import { PageShell } from "@/components/page-shell";
import BedrockPlayerHeader from "@/components/player/bedrock-player-header";
import PlayerSkin from "@/components/player/skin/player-skin";
import { SelectedCapeProvider } from "@/components/provider/selected-cape-provider";
import { SelectedSkinProvider } from "@/components/provider/selected-skin-provider";
import { Button } from "@/components/ui/button";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { StateBlock } from "@/components/ui/state-block";
import { UserRound } from "lucide-react";
import { Metadata } from "next";
import Link from "next/link";
import { cache } from "react";

export const revalidate = 300;

const getBedrockPlayer = cache(async (query: string) => {
  const { data: player, error } = await mcUtilsCachedApi.bedrock().player(query);
  return { player, error };
});

function isUnknownBedrockPlayer(error: Awaited<ReturnType<typeof getBedrockPlayer>>["error"]) {
  return isNotFoundError(error) || error?.code === 400;
}

function displayName(query: string) {
  return decodeURIComponent(query).replace(/^\./, "");
}

export async function generateMetadata(props: PageProps<"/bedrock/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { player, error } = await getBedrockPlayer(query);

  if (!player) {
    const unknown = isUnknownBedrockPlayer(error);

    return buildMetadata({
      title: unknown ? `${displayName(query)}: Bedrock Player Not Found` : "Bedrock player unavailable",
      description: unknown
        ? "No Minecraft Bedrock player matches this gamertag or Floodgate UUID. Search for another player on MC Utils."
        : "This Minecraft Bedrock player profile is temporarily unavailable. Try again in a moment.",
      path: `/bedrock/${query}`,
      noindex: true,
    });
  }

  const title = `${player.username}: Minecraft Bedrock Profile & Skin`;
  const description = `View ${player.username}'s Minecraft Bedrock skin, gamertag, and Floodgate UUID on MC Utils.`;

  return {
    ...buildMetadata({
      title,
      description,
      path: `/bedrock/${player.uniqueId}`,
      image: player.skin?.parts.FULLBODY_ISO_FRONT,
    }),
    ...(player.skin
      ? {
          icons: {
            icon: mcUtilsCachedApi.bedrock().skinUrl(player.uniqueId, "HEAD_ISO", { size: 64 }),
          },
        }
      : {}),
  };
}

export default async function BedrockPlayerPage({ params }: PageProps<"/bedrock/[query]">) {
  const { query } = await params;
  const { player, error } = await getBedrockPlayer(query);
  const name = displayName(query);

  if (!player) {
    const unknown = isUnknownBedrockPlayer(error);

    return (
      <PageShell>
        <StateBlock
          variant={unknown ? "not-found" : "error"}
          title={
            unknown ? (
              <>
                No Bedrock player called <b className="font-semibold">{name}</b>
              </>
            ) : (
              "Player unavailable"
            )
          }
          description={
            unknown
              ? "Only players who have joined a Geyser-enabled server can be found. Check the spelling, or look for a Java player instead."
              : "This profile could not be loaded. Try again in a moment."
          }
          action={
            unknown ? (
              <div className="flex flex-wrap items-center justify-center gap-2">
                <Button asChild>
                  <Link href="/">Look up another player</Link>
                </Button>
                <Button asChild variant="secondary">
                  <Link href={`/player/${encodeURIComponent(name)}`}>Try as Java player</Link>
                </Button>
              </div>
            ) : (
              <Button asChild variant="secondary">
                <Link href="/">Look up another player</Link>
              </Button>
            )
          }
        />
      </PageShell>
    );
  }

  const jsonLd = [
    {
      "@context": "https://schema.org",
      "@type": "ProfilePage",
      url: absoluteUrl(`/bedrock/${player.uniqueId}`),
      mainEntity: {
        "@type": "Person",
        name: player.username,
        identifier: player.uniqueId,
        ...(player.skin
          ? {
              image: mcUtilsCachedApi.bedrock().skinUrl(player.uniqueId, "HEAD_ISO", { size: 256 }),
            }
          : {}),
      },
    },
    breadcrumbJsonLd([
      { name: "Home", path: "/" },
      { name: player.username, path: `/bedrock/${player.uniqueId}` },
    ]),
  ];

  return (
    <>
      <JsonLd id="bedrock-player-jsonld" data={jsonLd} />
      <PageShell>
        <div className="flex w-full min-w-0 flex-col gap-10">
          <BedrockPlayerHeader player={player} />

          <div className="flex w-full min-w-0 flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
            <aside className="flex w-full flex-col gap-4 lg:w-90 lg:shrink-0">
              {player.skin ? (
                <SelectedCapeProvider initialCape={null}>
                  <SelectedSkinProvider key={player.skin.textureId} initialSkin={player.skin}>
                    <PlayerSkin player={player} />
                  </SelectedSkinProvider>
                </SelectedCapeProvider>
              ) : (
                <Card className="overflow-hidden p-0">
                  <CardHeader>Skin</CardHeader>
                  <CardContent className="flex h-full items-center justify-center">
                    <Empty>
                      <EmptyHeader>
                        <EmptyMedia variant="icon">
                          <UserRound aria-hidden />
                        </EmptyMedia>
                        <EmptyTitle>No skin on record</EmptyTitle>
                        <EmptyDescription>
                          We only see skins of players who have joined a server that uses Geyser. This player
                          hasn&apos;t yet.
                        </EmptyDescription>
                      </EmptyHeader>
                    </Empty>
                  </CardContent>
                </Card>
              )}
            </aside>

            <div className="flex min-w-0 flex-1 flex-col gap-4">
              <Card className="overflow-hidden p-0">
                <CardHeader>About this profile</CardHeader>
                <CardContent>
                  <p className="text-text-secondary max-w-[65ch] text-sm leading-6">
                    Bedrock profiles show the skin the player is wearing now. Skins come from Geyser, so the
                    one shown here can be older than the player&apos;s real skin. Bedrock has no name or skin
                    history and no capes.
                  </p>
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      </PageShell>
    </>
  );
}
