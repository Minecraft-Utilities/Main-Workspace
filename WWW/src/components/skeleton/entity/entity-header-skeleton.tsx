import Skeleton from "@/components/skeleton/ui/skeleton";

export interface EntityHeaderSkeletonProps {
  showDescription?: boolean;
}

export default function EntityHeaderSkeleton({ showDescription = true }: EntityHeaderSkeletonProps) {
  return (
    <header className="flex w-full min-w-0 flex-col gap-5">
      <div className="flex min-w-0 items-center gap-3 sm:gap-4">
        <Skeleton className="size-14 shrink-0 rounded-md sm:size-16" />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <Skeleton className="h-8 w-64 max-w-full rounded-md sm:w-80" />
          {showDescription ? <Skeleton className="h-4 w-40 rounded-md" /> : null}
        </div>
      </div>
      <div className="flex flex-wrap gap-x-8 gap-y-3">
        {Array.from({ length: 2 }).map((_, index) => (
          <div key={index} className="flex flex-col gap-1">
            <Skeleton className="h-4 w-20 rounded-md" />
            <Skeleton className="h-6 w-16 rounded-md" />
          </div>
        ))}
      </div>
    </header>
  );
}
