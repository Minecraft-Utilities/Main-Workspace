import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent } from "@/components/ui/card";

export default function ServerStatusSkeleton() {
  return (
    <section className="w-full min-w-0">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardContent className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2.5 text-sm">
          <Skeleton className="h-4 w-16 rounded" />
          <Skeleton className="h-4 w-32 rounded" />
          <Skeleton className="h-4 w-28 rounded" />
        </CardContent>
      </Card>
    </section>
  );
}
