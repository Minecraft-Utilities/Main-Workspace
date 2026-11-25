import { formatDate } from "@/common/time-utils";
import { formatNumberWithCommas } from "@/common/utils";
import TimeAgo from "@/components/time-ago";
import { EntityHeader } from "@/components/ui/entity-header";
import type { Cape } from "mcutils-js-api";
import { imageUrl } from "mcutils-js-api";
import Image from "next/image";
import Link from "next/link";

export interface CapeHeaderProps {
  cape: Cape;
}

export default function CapeHeader({ cape }: CapeHeaderProps) {
  const firstSeen = new Date(cape.firstSeen);
  const name = cape.name ?? "Unknown Cape";

  return (
    <EntityHeader
      media={
        <Image
          src={imageUrl(cape.parts.FRONT, { size: 128 })}
          alt={`${name} preview`}
          width={64}
          height={64}
          className="pixelated size-full object-contain"
          priority
        />
      }
      title={name}
      description={
        cape.firstSeenUsing ? (
          <>
            First seen wearing{" "}
            <Link
              href={`/player/${encodeURIComponent(cape.firstSeenUsing)}`}
              className="text-link hover:underline"
            >
              {cape.firstSeenUsing}
            </Link>
          </>
        ) : (
          "Minecraft cape"
        )
      }
      stats={[
        {
          label: "Owners",
          value: formatNumberWithCommas(cape.currentOwners ?? 0),
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
