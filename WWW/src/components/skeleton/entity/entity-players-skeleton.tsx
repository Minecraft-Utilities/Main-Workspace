import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

const ROWS = 8;

export default function EntityPlayersSkeleton() {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader className="justify-between gap-3">
        <div className="flex items-center gap-2">
          <Skeleton className="h-4 w-16 rounded-md" />
          <Skeleton className="h-3 w-10 rounded-md" />
        </div>
        <Skeleton className="h-8 w-44 shrink-0 rounded-sm" />
      </CardHeader>
      <CardContent className="p-0">
        <div className="divide-border/60 divide-y">
          {Array.from({ length: ROWS }).map((_, index) => (
            <div key={index} className="flex items-center gap-3 px-4 py-2">
              <Skeleton className="size-8 shrink-0 rounded-sm" />
              <Skeleton className="h-4 w-32 rounded-md" />
              <Skeleton className="ml-auto h-3 w-16 rounded-md" />
            </div>
          ))}
        </div>
        <div className="border-border border-t px-4 py-3">
          <PaginationSkeleton />
        </div>
      </CardContent>
    </Card>
  );
}
