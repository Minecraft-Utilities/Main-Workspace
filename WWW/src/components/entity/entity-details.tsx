import { formatFullTimestamp, formatNumberWithCommas } from "@/common/utils";
import { ProfileField, ProfileFields, ProfileValue } from "@/components/profile-field";
import SimpleLink from "@/components/simple-link";
import SimpleTooltip from "@/components/simple-tooltip";
import TimeAgo from "@/components/time-ago";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export type EntityKind = "cape" | "skin";

export interface EntityDetailData {
  firstSeen: string;
  firstSeenUsing?: string;
  currentOwners?: number;
  uniqueOwners: number;
}

const LABELS: Record<EntityKind, { firstSeenUsing: string; currentOwners: string; uniqueOwners: string }> = {
  cape: {
    firstSeenUsing: "First Seen Owning",
    currentOwners: "Current Wearers",
    uniqueOwners: "Total Wearers",
  },
  skin: {
    firstSeenUsing: "First Seen Using",
    currentOwners: "Current Users",
    uniqueOwners: "Total Users",
  },
};

export interface EntityDetailsProps {
  kind: EntityKind;
  entity: EntityDetailData;
}

export default function EntityDetails({ kind, entity }: EntityDetailsProps) {
  const labels = LABELS[kind];
  const firstSeen = new Date(entity.firstSeen);

  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Details</CardHeader>
      <CardContent className="p-3 pt-2">
        <ProfileFields>
          {entity.firstSeenUsing && (
            <ProfileField label={labels.firstSeenUsing}>
              <SimpleLink
                href={`/player/${entity.firstSeenUsing}`}
                className="text-foreground/75 hover:text-foreground font-mono underline-offset-4 hover:underline"
              >
                {entity.firstSeenUsing}
              </SimpleLink>
            </ProfileField>
          )}

          <ProfileField label={labels.currentOwners}>
            <ProfileValue>{formatNumberWithCommas(entity.currentOwners ?? 0)}</ProfileValue>
          </ProfileField>

          <ProfileField label={labels.uniqueOwners}>
            <ProfileValue>{formatNumberWithCommas(entity.uniqueOwners)}</ProfileValue>
          </ProfileField>

          <ProfileField label="First Recorded">
            <SimpleTooltip display={formatFullTimestamp(firstSeen)} className="justify-start">
              <span className="text-foreground/75 cursor-default font-normal">
                <TimeAgo date={firstSeen} rounded />
              </span>
            </SimpleTooltip>
          </ProfileField>
        </ProfileFields>
      </CardContent>
    </Card>
  );
}
