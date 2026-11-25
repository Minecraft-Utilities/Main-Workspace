import type { Cape } from "mcutils-js-api";
import OwnerCount from "../owner-count";
import OwnerTiles from "../skin/owner-tiles";
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
      <CardContent className="flex min-h-0 flex-1 flex-col">
        <OwnerTiles accounts={accounts} remaining={remainingOwners} />
      </CardContent>
    </Card>
  );
}
