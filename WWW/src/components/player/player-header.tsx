import type { FullPlayer } from "mcutils-js-api";
import Image from "next/image";
import PlayerUuid from "./player-uuid";

export interface PlayerHeaderProps {
  player: FullPlayer;
}

export default function PlayerHeader({ player }: PlayerHeaderProps) {
  return (
    <header className="w-full text-left">
      <div className="flex items-center gap-3 sm:gap-4">
        <Image src={player.skin.parts.HEAD_ISO} alt="" width={56} height={56} className="shrink-0" priority />
        <div className="min-w-0">
          <h1 className="text-foreground text-4xl font-bold tracking-tight">{player.username}</h1>
          <PlayerUuid uuid={player.uniqueId} />
        </div>
      </div>
    </header>
  );
}
