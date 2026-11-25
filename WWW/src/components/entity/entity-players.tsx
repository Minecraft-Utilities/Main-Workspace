import OwnerTiles from "@/components/skin/owner-tiles";

export interface EntityPlayersProps {
  kind: "cape" | "skin";
  accounts: string[];
  currentOwners: number;
}

export default function EntityPlayers({ kind, accounts, currentOwners }: EntityPlayersProps) {
  return <OwnerTiles kind={kind} accounts={accounts} currentOwners={currentOwners} />;
}
