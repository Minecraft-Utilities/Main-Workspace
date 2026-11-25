import type { Skin } from "mcutils-js-api";
import OwnerCount from "../owner-count";
import Card, { CardContent, CardHeader } from "../ui/card";
import OwnerTiles from "./owner-tiles";

interface SkinPlayersProps {
  skin: Skin;
}

export default function SkinPlayers({ skin }: SkinPlayersProps) {
  const accounts = skin.accountsSeenUsing ?? [];
  const currentOwners = skin.currentOwners ?? 0;
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
