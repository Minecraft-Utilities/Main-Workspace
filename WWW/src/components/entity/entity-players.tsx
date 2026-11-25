import OwnerCount from "@/components/owner-count";
import OwnerTiles from "@/components/skin/owner-tiles";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export interface EntityPlayersProps {
  kind: "cape" | "skin";
  accounts: string[];
  currentOwners: number;
}

export default function EntityPlayers({ kind, accounts, currentOwners }: EntityPlayersProps) {
  const noun = kind === "cape" ? "Wearers" : "Users";
  const remaining = currentOwners - accounts.length;

  return (
    <Card className="min-h-105 w-full text-sm">
      <CardHeader>
        <OwnerCount count={currentOwners} name={noun} />
      </CardHeader>
      <CardContent className="flex min-h-0 flex-1 flex-col">
        <OwnerTiles kind={kind} accounts={accounts} remaining={remaining} />
      </CardContent>
    </Card>
  );
}
