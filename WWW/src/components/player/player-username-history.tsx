import { formatDate } from "@/common/time-utils";
import { CopyButton } from "@/components/copy-button";
import SimpleTooltip from "@/components/simple-tooltip";
import TimeAgo from "@/components/time-ago";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { StateBlock } from "@/components/ui/state-block";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { FullPlayer } from "mcutils-js-api";

export interface PlayerUsernameHistoryProps {
  player: FullPlayer;
}

export default function PlayerUsernameHistory({ player }: PlayerUsernameHistoryProps) {
  const sorted = [...(player.usernameHistory ?? [])].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );

  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Name history</CardHeader>
      <CardContent className="p-0">
        {sorted.length === 0 ? (
          <StateBlock
            variant="empty"
            title="No name changes yet"
            description="We haven't recorded a username change for this player."
            className="rounded-none border-0 bg-transparent shadow-none"
          />
        ) : (
          <Table>
            <TableHeader className="bg-card">
              <TableRow className="hover:bg-transparent">
                <TableHead className="pl-4">Name</TableHead>
                <TableHead>Seen</TableHead>
                <TableHead className="w-12 pr-4">
                  <span className="sr-only">Copy</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((entry, index) => {
                const seenAt = new Date(entry.timestamp);
                // The oldest name's timestamp is when the player was first recorded, not when that name was seen.
                const isFirstName = index === sorted.length - 1;

                return (
                  <TableRow key={`${entry.newUsername}-${entry.timestamp}`}>
                    <TableCell className="text-foreground pl-4 font-medium">{entry.newUsername}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {isFirstName ? null : (
                        <SimpleTooltip
                          display={formatDate(seenAt, "Do MMMM, YYYY HH:mm a")}
                          className="justify-start"
                        >
                          <TimeAgo date={seenAt} rounded />
                        </SimpleTooltip>
                      )}
                    </TableCell>
                    <TableCell className="pr-2 text-right">
                      <CopyButton
                        variant="icon"
                        value={entry.newUsername}
                        label={`Copy ${entry.newUsername}`}
                        className="text-muted-foreground"
                      />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
