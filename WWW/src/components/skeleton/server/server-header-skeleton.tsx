import Skeleton from "@/components/skeleton/ui/skeleton";

export default function ServerHeaderSkeleton() {
  return (
    <header className="flex w-full min-w-0 flex-col gap-5">
      <div className="flex min-w-0 items-start gap-3 sm:items-center sm:gap-4">
        <Skeleton className="bg-stage size-14 shrink-0 rounded-md sm:size-16" />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <Skeleton className="h-9 w-56 max-w-full rounded-md sm:w-80" />
          <Skeleton className="h-7 w-64 max-w-full rounded-sm" />
        </div>
      </div>
      <div className="flex flex-wrap gap-8">
        {Array.from({ length: 2 }).map((_, i) => (
          <div key={i} className="flex flex-col gap-1.5">
            <Skeleton className="h-4 w-24 rounded-md" />
            <Skeleton className="h-6 w-20 rounded-md" />
          </div>
        ))}
      </div>
    </header>
  );
}
