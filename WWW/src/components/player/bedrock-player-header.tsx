import { formatDate } from "@/common/time-utils";
import TimeAgo from "@/components/time-ago";
import { EntityHeader } from "@/components/ui/entity-header";
import type { StatItem } from "@/components/ui/stat-row";
import { UserRound } from "lucide-react";
import type { BedrockPlayer } from "mcutils-js-api";
import { imageUrl } from "mcutils-js-api";
import Image from "next/image";

export interface BedrockPlayerHeaderProps {
  player: BedrockPlayer;
}

export default function BedrockPlayerHeader({ player }: BedrockPlayerHeaderProps) {
  const head = player.skin?.parts.HEAD_ISO;
  const stats: StatItem[] = [];

  if (player.skinUpdatedAt) {
    const updatedAt = new Date(player.skinUpdatedAt);
    stats.push({
      label: "Skin updated",
      value: <TimeAgo date={updatedAt} short />,
      title: formatDate(updatedAt, "Do MMMM, YYYY"),
    });
  }

  if (player.skin) {
    stats.push({
      label: "Skin model",
      value: player.skin.model === "SLIM" ? "Slim" : "Classic",
    });
  }

  return (
    <EntityHeader
      media={
        head ? (
          <Image
            src={imageUrl(head, { size: 128 })}
            alt={`${player.username}'s Minecraft Bedrock head`}
            width={64}
            height={64}
            className="pixelated size-full object-contain"
            priority
          />
        ) : (
          <UserRound className="text-text-faint size-6 sm:size-8" aria-hidden />
        )
      }
      title={player.username}
      badge={
        <span className="bg-control border-border text-muted-foreground rounded-sm border px-1.5 py-0.5 font-mono text-xs font-medium">
          Bedrock
        </span>
      }
      identifiers={[
        { label: "Floodgate UUID", value: player.uniqueId },
        { label: "XUID", value: player.xuid },
      ]}
      stats={stats}
    />
  );
}
