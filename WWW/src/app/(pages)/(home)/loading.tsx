import { PageShell } from "@/components/page-shell";
import SkinCardSkeleton from "@/components/skeleton/skins/skin-card-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import { MediaGrid } from "@/components/ui/media-grid";

export default function HomeLoading() {
  return (
    <div aria-live="polite" aria-busy="true">
      <PageShell>
        <div className="flex w-full flex-col items-center gap-6">
          <Skeleton className="h-[38px] w-96 max-w-full rounded-sm md:h-12" />
          <div className="flex w-full max-w-xl flex-col items-center gap-3">
            <Skeleton className="h-12 w-full rounded-sm" />
            <div className="flex items-center gap-2">
              <Skeleton className="h-8 w-10 rounded-sm" />
              <Skeleton className="h-8 w-16 rounded-sm" />
              <Skeleton className="h-8 w-24 rounded-sm" />
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-start justify-center gap-x-12 gap-y-4">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="flex flex-col items-center gap-1">
              <Skeleton className="h-7 w-24 rounded-sm" />
              <Skeleton className="h-4 w-20 rounded-sm" />
            </div>
          ))}
        </div>

        <div className="grid w-full grid-cols-2 gap-4 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-lg" />
          ))}
        </div>

        <div className="flex w-full flex-col gap-4">
          <div className="flex items-center justify-between gap-4">
            <Skeleton className="h-7 w-36" />
            <Skeleton className="h-10 w-24 rounded-sm" />
          </div>
          <MediaGrid>
            {Array.from({ length: 10 }).map((_, i) => (
              <SkinCardSkeleton key={i} />
            ))}
          </MediaGrid>
        </div>
      </PageShell>
    </div>
  );
}
