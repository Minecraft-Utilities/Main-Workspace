import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent } from "@/components/ui/card";
import { UserIcon } from "lucide-react";

export default function SkinCardSkeleton() {
  return (
    <Card className="w-full overflow-hidden">
      <CardContent className="flex min-h-0 flex-1 flex-col p-0">
        <div className="bg-stage relative flex aspect-[3/4] w-full items-center justify-center p-4">
          <Skeleton className="h-2/3 w-1/2 rounded-sm" />
        </div>
        <div className="flex items-center gap-1 px-3 py-2.5">
          <UserIcon className="text-muted-foreground size-3.5" aria-hidden />
          <Skeleton className="h-4 w-16 rounded-sm" />
        </div>
      </CardContent>
    </Card>
  );
}
