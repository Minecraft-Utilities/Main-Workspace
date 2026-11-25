import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function SkinHeadCommandsSkeleton() {
  return (
    <Card>
      <CardHeader>Head commands</CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex gap-2">
          <Skeleton className="h-9 min-w-0 flex-1" />
          <Skeleton className="h-9 w-20 shrink-0" />
        </div>
        <Skeleton className="h-16 w-full" />
      </CardContent>
    </Card>
  );
}
