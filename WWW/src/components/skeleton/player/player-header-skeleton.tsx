import Skeleton from "@/components/skeleton/ui/skeleton";

export default function PlayerHeaderSkeleton() {
  return (
    <header className="flex w-full min-w-0 flex-col gap-5">
      <div className="flex min-w-0 items-start gap-3 sm:items-center sm:gap-4">
        <Skeleton className="bg-stage size-14 shrink-0 rounded-md sm:size-16" />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <Skeleton className="h-9 w-48 rounded-md sm:w-64" />
          <Skeleton className="h-7 w-72 max-w-full rounded-sm" />
        </div>
      </div>
      <div className="flex flex-wrap gap-8">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex flex-col gap-1.5">
            <Skeleton className="h-4 w-16 rounded-md" />
            <Skeleton className="h-6 w-20 rounded-md" />
          </div>
        ))}
      </div>
    </header>
  );
}
