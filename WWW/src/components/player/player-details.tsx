import { formatNumberWithCommas } from "@/common/utils";
import { ProfileField, ProfileFields, ProfileValue } from "@/components/profile-field";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import type { FullPlayer } from "mcutils-js-api";

export interface PlayerDetailsProps {
  player: FullPlayer;
}

export default function PlayerDetails({ player }: PlayerDetailsProps) {
  if (player.monthlyViews <= 0 && player.submittedUuids <= 0) {
    return null;
  }

  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Details</CardHeader>
      <CardContent className="p-3 pt-2">
        <ProfileFields>
          {player.monthlyViews > 0 && (
            <ProfileField label="Views" tooltip="Profile page views in the last 30 days.">
              <ProfileValue>{formatNumberWithCommas(player.monthlyViews)} / month</ProfileValue>
            </ProfileField>
          )}

          {player.submittedUuids > 0 && (
            <ProfileField
              label="Submitted UUIDs"
              tooltip="Number of player UUIDs submitted to MC Utils by the community."
            >
              <ProfileValue>{formatNumberWithCommas(player.submittedUuids)}</ProfileValue>
            </ProfileField>
          )}
        </ProfileFields>
      </CardContent>
    </Card>
  );
}
