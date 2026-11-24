"use client";

import { mcUtilsApi } from "@/common/mc-utils";
import { useQuery } from "@tanstack/react-query";
import { ReactNode, useEffect, useState } from "react";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "../ui/hover-card";

export default function PlayerPreview({ nameOrUuid, trigger }: { nameOrUuid: string; trigger: ReactNode }) {
  const [open, setOpen] = useState(false);
  const { data } = useQuery({
    queryKey: ["player", nameOrUuid],
    queryFn: async () => mcUtilsApi.player().fetch(nameOrUuid),
    enabled: open,
  });

  useEffect(() => {
    if (!open) {
      return;
    }
    // Scrolling moves the trigger out from under a stationary cursor without firing pointerleave, so Radix never closes the card.
    const close = () => setOpen(false);
    window.addEventListener("scroll", close, { capture: true, passive: true });
    return () => window.removeEventListener("scroll", close, { capture: true });
  }, [open]);

  const player = data?.data;
  return (
    <HoverCard openDelay={200} closeDelay={200} onOpenChange={setOpen} open={open && !!data}>
      <HoverCardTrigger asChild>{trigger}</HoverCardTrigger>
      {player && (
        <HoverCardContent className="flex w-85 flex-col gap-4 rounded-xl">
          <img src={player.skin.parts.FACE} className="size-14" alt="" />

          <div className="gap-05 flex flex-col">
            <p>{player.username}</p>
            <p className="text-muted-foreground text-sm">{player.uniqueId}</p>
          </div>
        </HoverCardContent>
      )}
    </HoverCard>
  );
}
