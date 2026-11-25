import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

const ROWS = [
  { label: "w-24", value: "w-20" },
  { label: "w-28", value: "w-8" },
  { label: "w-20", value: "w-8" },
  { label: "w-24", value: "w-16" },
];

export default function EntityDetailsSkeleton() {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Details</CardHeader>
      <CardContent className="p-3 pt-2">
        <div className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-6 px-1 text-sm">
          {ROWS.map(({ label, value }, index) => (
            <div
              key={index}
              className="col-span-2 grid grid-cols-subgrid items-center py-2 first:pt-0 last:pb-0"
            >
              <Skeleton className={`h-4 ${label} rounded`} />
              <Skeleton className={`h-4 ${value} rounded`} />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
