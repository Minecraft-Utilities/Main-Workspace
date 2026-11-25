import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent } from "@/components/ui/card";

export default function PreviewSkeleton() {
  return (
    <Card className="min-h-105 w-full">
      <CardContent className="flex items-center justify-center p-4">
        <Skeleton className="h-96 w-full max-w-64" />
      </CardContent>
    </Card>
  );
}
