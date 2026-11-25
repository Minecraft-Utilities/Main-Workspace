"use client";
import { imageUrl } from "mcutils-js-api";

import { cn } from "@/common/utils";
import type { FullPlayer } from "mcutils-js-api";
import Image from "next/image";
import { useRef } from "react";
import { useSelectedCape } from "../provider/selected-cape-provider";
import SimpleTooltip from "../simple-tooltip";
import TimeAgo from "../time-ago";
import Card, { CardContent, CardHeader } from "../ui/card";

export interface PlayerCapesProps {
  player: FullPlayer;
}

export const CAPE_ASPECT_RATIO = 480 / 768;

export default function PlayerCapes({ player }: PlayerCapesProps) {
  const { selectedCape, setSelectedCape, setHoveredCape } = useSelectedCape();
  const hoverClearTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const capes =
    player.capeHistory?.sort((a, b) => new Date(b.firstSeen).getTime() - new Date(a.firstSeen).getTime()) ??
    [];

  if (capes.length === 0) {
    return (
      <Card className="h-fit min-w-48 overflow-hidden p-0">
        <CardHeader>Capes (0)</CardHeader>
        <CardContent className="flex h-full items-center justify-center gap-2">
          <p className="text-muted-foreground py-2 text-center text-sm">No capes recorded yet.</p>
        </CardContent>
      </Card>
    );
  }

  function handleCapeHoverEnter(cape: (typeof capes)[number]) {
    if (hoverClearTimeout.current) clearTimeout(hoverClearTimeout.current);
    setHoveredCape(cape);
  }

  function handleCapeHoverLeave() {
    hoverClearTimeout.current = setTimeout(() => {
      setHoveredCape(null);
    }, 80);
  }

  return (
    <Card className="h-fit min-w-48 overflow-hidden p-0">
      <CardHeader>Capes ({capes.length})</CardHeader>
      <CardContent className="flex h-full flex-wrap items-center justify-center gap-2">
        {capes.map(cape => {
          const isSelected = selectedCape?.textureId === cape.textureId;
          const capeLabel = cape.name ? `${cape.name} Cape` : "Unknown Cape";

          return (
            <div key={cape.textureId} className="w-10 shrink-0">
              <SimpleTooltip
                display={
                  <div className="flex flex-col gap-2">
                    <p className={cn("text-sm font-medium", isSelected ? "text-primary" : "")}>
                      {isSelected ? "Currently selected" : `${capeLabel} - Click to preview`}
                    </p>
                    <div className="flex flex-col gap-0.5">
                      <div className="flex items-center justify-between gap-4 text-xs">
                        <span className="text-muted-foreground">First Seen</span>
                        <span className="font-medium">
                          <TimeAgo date={new Date(cape.firstSeen)} />
                        </span>
                      </div>
                    </div>
                  </div>
                }
              >
                <button
                  type="button"
                  onClick={() => setSelectedCape(cape)}
                  onMouseEnter={() => handleCapeHoverEnter(cape)}
                  onMouseLeave={handleCapeHoverLeave}
                  aria-pressed={isSelected}
                  aria-label={`Preview ${capeLabel}`}
                  className="focus-visible:ring-ring rounded-sm focus-visible:ring-2 focus-visible:outline-none"
                >
                  <Image
                    src={imageUrl(cape.parts.FRONT, { size: 128 })}
                    alt={`${player.username}'s ${capeLabel}`}
                    width={64 * CAPE_ASPECT_RATIO}
                    height={64}
                    className={cn(
                      "h-16 rounded-sm object-cover ring-2",
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
