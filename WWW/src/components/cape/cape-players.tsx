import { formatNumberWithCommas } from "@/common/utils";
import type { Cape } from "mcutils-js-api";
import OwnerCount from "../owner-count";
import PlayerPreview from "../player/player-preview";
import SimpleLink from "../simple-link";
import Card, { CardContent, CardHeader } from "../ui/card";

interface CapePlayersProps {
  cape: Cape;
}

export default function CapePlayers({ cape }: CapePlayersProps) {
  const accounts = cape.accountsSeenOwning ?? [];
  const currentOwners = cape.currentOwners ?? 0;
  const remainingOwners = currentOwners - accounts.length;

  return (
    <Card className="h-105 w-full text-sm">
      <CardHeader>
        <OwnerCount count={currentOwners} name="Owners" />
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2 overflow-y-auto">
        {accounts.map((account, index) => (
          <div key={account} className="flex items-center">
            <PlayerPreview
              nameOrUuid={account}
              trigger={
                <SimpleLink href={`/player/${account}`}>
                  <p>{account}</p>
                </SimpleLink>
              }
            />
            {index !== accounts.length - 1 && <p className="text-muted-foreground">,</p>}
          </div>
        ))}
        {remainingOwners > 0 && (
          <p className="text-muted-foreground">+ {formatNumberWithCommas(remainingOwners)} more...</p>
        )}
      </CardContent>
    </Card>
  );
}
