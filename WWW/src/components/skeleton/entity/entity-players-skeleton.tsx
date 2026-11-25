import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { UserIcon } from "lucide-react";

export default function EntityPlayersSkeleton() {
  return (
    <Card className="min-h-105 w-full text-sm">
      <CardHeader>
        <div className="flex items-center gap-1">
          <UserIcon className="text-primary size-3.5" aria-hidden />
          <Skeleton className="h-3 w-16" />
        </div>
      </CardHeader>
      <CardContent className="flex min-h-0 flex-1 flex-col gap-2">
        <Skeleton className="h-8 w-full shrink-0 rounded-md" />
        <div className="grid auto-rows-min grid-cols-2 content-start gap-1.5 sm:grid-cols-3">
          {Array.from({ length: 9 }).map((_, index) => (
            <Skeleton key={index} className="h-9 rounded-md" />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
