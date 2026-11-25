import { imageUrl } from "mcutils-js-api";
("use client");

import { cn } from "@/common/utils";
import type { FullPlayer } from "mcutils-js-api";
import Image from "next/image";
import { useRef } from "react";
import { useSelectedSkin } from "../../provider/selected-skin-provider";
import SimpleTooltip from "../../simple-tooltip";
import TimeAgo from "../../time-ago";
import Card, { CardContent, CardHeader } from "../../ui/card";

export interface PlayerSkinsProps {
  player: FullPlayer;
}

export default function PlayerSkins({ player }: PlayerSkinsProps) {
  const { selectedSkin, setSelectedSkin, setHoveredSkin } = useSelectedSkin();
  const hoverClearTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const skins =
    player.skinHistory?.sort((a, b) => new Date(b.firstSeen).getTime() - new Date(a.firstSeen).getTime()) ??
    [];

  if (skins.length === 0) {
    return (
      <Card className="h-fit min-w-48 overflow-hidden p-0">
        <CardHeader>Skins (0)</CardHeader>
        <CardContent className="flex h-full items-center justify-center gap-2">
          <p className="text-muted-foreground py-2 text-center text-sm">No skin history recorded yet.</p>
        </CardContent>
      </Card>
    );
  }

  function handleSkinHoverEnter(skin: (typeof skins)[number]) {
    if (hoverClearTimeout.current) clearTimeout(hoverClearTimeout.current);
    setHoveredSkin(skin);
  }

  function handleSkinHoverLeave() {
    hoverClearTimeout.current = setTimeout(() => {
      setHoveredSkin(null);
    }, 80);
  }

  return (
    <Card className="h-fit min-w-48 overflow-hidden p-0">
      <CardHeader>Skins ({skins.length})</CardHeader>
      <CardContent className="flex h-full flex-wrap items-center justify-center gap-2">
        {skins.map(skin => {
          const isSelected = selectedSkin?.textureId === skin.textureId;
          const skinLabel = skin.model === "SLIM" ? "Slim skin" : "Classic skin";

          return (
            <div key={skin.textureId} className="size-[38px] shrink-0">
              <SimpleTooltip
                display={
                  <div className="flex flex-col gap-2">
                    <p className={cn("text-sm font-medium", isSelected ? "text-primary" : "")}>
                      {isSelected ? "Currently selected" : `${skinLabel} - Click to preview`}
                    </p>
                    <div className="flex flex-col gap-0.5">
                      <div className="flex items-center justify-between gap-4 text-xs">
                        <span className="text-muted-foreground">First Seen</span>
                        <span className="font-medium">
                          <TimeAgo date={new Date(skin.firstSeen)} />
                        </span>
                      </div>
                    </div>
                  </div>
                }
              >
                <button
                  type="button"
                  onClick={() => setSelectedSkin(skin)}
                  onMouseEnter={() => handleSkinHoverEnter(skin)}
                  onMouseLeave={handleSkinHoverLeave}
                  aria-pressed={isSelected}
                  aria-label={`Preview ${skinLabel}`}
                  className="focus-visible:ring-ring rounded-sm focus-visible:ring-2 focus-visible:outline-none"
                >
                  <Image
                    src={imageUrl(skin.parts.FACE, { size: 96 })}
                    alt={`${player.username}'s ${skinLabel}`}
                    width={38}
                    height={38}
                    className={cn(
                      "rounded-sm object-cover ring-2",
                      isSelected ? "ring-primary" : "ring-transparent"
                    )}
                  />
                </button>
              </SimpleTooltip>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
