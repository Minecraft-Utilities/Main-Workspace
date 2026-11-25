import { formatDate } from "@/common/time-utils";
import { formatNumberWithCommas } from "@/common/utils";
import TimeAgo from "@/components/time-ago";
import { EntityHeader } from "@/components/ui/entity-header";
import type { Skin } from "mcutils-js-api";
import { imageUrl } from "mcutils-js-api";
import Image from "next/image";
import Link from "next/link";

export interface SkinHeaderProps {
  skin: Skin;
}

export default function SkinHeader({ skin }: SkinHeaderProps) {
  const firstSeen = new Date(skin.firstSeen);
  const name = skin.firstSeenUsing ? `${skin.firstSeenUsing}'s Skin` : "Minecraft Skin";

  return (
    <EntityHeader
      media={
        <Image
          src={imageUrl(skin.parts.HEAD_ISO, { size: 128 })}
          alt={`${name} render`}
          width={64}
          height={64}
          className="pixelated size-full object-contain"
          priority
        />
      }
      title={name}
      description={
        skin.firstSeenUsing ? (
          <>
            First seen using{" "}
            <Link
              href={`/player/${encodeURIComponent(skin.firstSeenUsing)}`}
              className="text-link hover:underline"
            >
              {skin.firstSeenUsing}
            </Link>
          </>
        ) : (
          "Minecraft skin"
        )
      }
      stats={[
        {
          label: "Owners",
          value: formatNumberWithCommas(skin.currentOwners ?? 0),
        },
        {
          label: "First recorded",
          value: <TimeAgo date={firstSeen} short />,
          title: formatDate(firstSeen, "Do MMMM, YYYY"),
        },
      ]}
    />
  );
}
