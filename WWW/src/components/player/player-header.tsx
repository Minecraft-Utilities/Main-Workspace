import { formatDate } from "@/common/time-utils";
import { formatNumberWithCommas } from "@/common/utils";
import TimeAgo from "@/components/time-ago";
import { EntityHeader } from "@/components/ui/entity-header";
import type { FullPlayer } from "mcutils-js-api";
import { imageUrl } from "mcutils-js-api";
import Image from "next/image";

export interface PlayerHeaderProps {
  player: FullPlayer;
}

export default function PlayerHeader({ player }: PlayerHeaderProps) {
  const firstSeen = new Date(player.firstSeen);

  return (
    <EntityHeader
      media={
        <Image
          src={imageUrl(player.skin.parts.HEAD_ISO, { size: 128 })}
          alt={`${player.username}'s Minecraft head`}
          width={64}
          height={64}
          className="pixelated size-full object-contain"
          priority
        />
      }
      title={player.username}
      identifiers={[{ label: "UUID", value: player.uniqueId }]}
      stats={[
        {
          label: "First seen",
          value: <TimeAgo date={firstSeen} short />,
          title: formatDate(firstSeen, "Do MMMM, YYYY"),
        },
        {
          label: "Skins",
          value: formatNumberWithCommas(player.skinHistory?.length ?? 0),
        },
        {
          label: "Capes",
          value: formatNumberWithCommas(player.capeHistory?.length ?? 0),
        },
      ]}
    />
  );
}
