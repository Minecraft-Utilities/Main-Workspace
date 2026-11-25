import Skeleton from "@/components/skeleton/ui/skeleton";

export default function ServerMotdSkeleton() {
  return (
    <div className="bg-stage border-border w-full overflow-hidden rounded-lg border">
      <Skeleton className="aspect-[768/87] w-full rounded-lg" />
    </div>
  );
}
