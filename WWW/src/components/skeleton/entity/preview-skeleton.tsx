import Skeleton from "@/components/skeleton/ui/skeleton";
import Card from "@/components/ui/card";

export default function PreviewSkeleton() {
  return (
    <Card className="w-full overflow-hidden p-0">
      <div className="bg-stage flex min-h-105 items-center justify-center bg-[radial-gradient(circle_at_50%_42%,var(--stage-highlight),var(--stage)_72%)] p-4">
        <Skeleton className="h-96 w-full max-w-64" />
      </div>
    </Card>
  );
}
