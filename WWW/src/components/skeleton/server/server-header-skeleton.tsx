import Skeleton from "@/components/skeleton/ui/skeleton";

export default function ServerHeaderSkeleton() {
  return (
    <div className="flex w-full min-w-0 items-center gap-3 sm:gap-4">
      <Skeleton className="size-14 shrink-0 rounded-md" />
      <Skeleton className="h-9 w-56 max-w-full rounded-md sm:h-10 sm:w-80" />
    </div>
  );
}
