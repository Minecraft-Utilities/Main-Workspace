import TableSkeleton from "@/components/skeleton/ui/table-skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function PlayerUsernameHistorySkeleton() {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Name history</CardHeader>
      <CardContent className="p-0">
        <TableSkeleton columns={3} rows={5} />
      </CardContent>
    </Card>
  );
}
