import { PageShell } from "@/components/page-shell";
import Skeleton from "@/components/skeleton/ui/skeleton";

export default function IpLoading() {
  return (
    <PageShell>
      <Skeleton className="h-10 w-full" />
      <div className="flex w-full min-w-0 flex-col gap-5" aria-busy="true">
        <div className="flex items-center gap-3 sm:gap-4">
          <Skeleton className="bg-stage size-14 shrink-0 rounded-md sm:size-16" />
          <div className="flex min-w-0 flex-1 flex-col gap-2">
            <Skeleton className="h-9 w-64 max-w-full rounded-md" />
            <Skeleton className="h-7 w-80 max-w-full rounded-sm" />
          </div>
        </div>
        <div className="flex flex-wrap gap-8">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex flex-col gap-1.5">
              <Skeleton className="h-4 w-20 rounded-md" />
              <Skeleton className="h-6 w-28 rounded-md" />
            </div>
          ))}
        </div>
      </div>
      <div className="flex w-full min-w-0 flex-col gap-4">
        <Skeleton className="h-64 w-full rounded-lg" />
        <Skeleton className="h-36 w-full rounded-lg" />
      </div>
    </PageShell>
  );
}
