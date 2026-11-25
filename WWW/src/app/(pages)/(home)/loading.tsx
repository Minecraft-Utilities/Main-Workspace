import { MEDIA_GRID } from "@/components/media-card";
import { PageShell } from "@/components/page-shell";
import Skeleton from "@/components/skeleton/ui/skeleton";

export default function HomeLoading() {
  return (
    <div aria-live="polite" aria-busy="true">
      <PageShell>
        <Skeleton className="h-9 w-80 max-w-full" />

        <div className="flex w-full max-w-xl flex-col items-center gap-5">
          <Skeleton className="h-28 w-full rounded-2xl" />
          <Skeleton className="h-5 w-72 max-w-full" />
        </div>

        <div className="grid w-full max-w-xl grid-cols-1 gap-3 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>

        <div className="flex w-full flex-col gap-4">
          <div className="flex items-center justify-between">
            <Skeleton className="h-6 w-40" />
            <Skeleton className="h-9 w-24 rounded-md" />
          </div>
          <div className={MEDIA_GRID}>
            {Array.from({ length: 10 }).map((_, i) => (
              <Skeleton key={i} className="h-60 rounded-2xl" />
            ))}
          </div>
        </div>
      </PageShell>
    </div>
  );
}
