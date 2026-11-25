import { PageTitle } from "@/components/page-shell";
import type { FullPlayer } from "mcutils-js-api";
import Image from "next/image";
import PlayerUuid from "./player-uuid";

export interface PlayerHeaderProps {
  player: FullPlayer;
}

export default function PlayerHeader({ player }: PlayerHeaderProps) {
  return (
    <div className="flex w-full min-w-0 items-center gap-3 sm:gap-4">
      <Image
        src={player.skin.parts.HEAD_ISO}
        alt=""
        width={56}
        height={56}
        className="size-14 shrink-0"
        priority
      />
      <div className="min-w-0 flex-1">
        <PageTitle className="min-w-0">
          <span className="block wrap-break-word">{player.username}</span>
        </PageTitle>
        <PlayerUuid uuid={player.uniqueId} />
      </div>
    </div>
  );
}
