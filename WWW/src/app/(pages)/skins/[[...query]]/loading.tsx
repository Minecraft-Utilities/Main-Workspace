import { MEDIA_GRID } from "@/components/media-card";
import { PageShell, PageTitle } from "@/components/page-shell";
import SkinCardSkeleton from "@/components/skeleton/skins/skin-card-skeleton";
import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import { SKIN_SORT_OPTIONS } from "@/components/skins/skin-sort-options";

export default function SkinsLoading() {
  return (
    <div aria-live="polite" aria-busy="true">
      <PageShell>
        <PageTitle>Minecraft Skins</PageTitle>

        <div className="flex w-full flex-col gap-6">
          <div className="flex flex-col items-start gap-2">
            <h2 className="text-muted-foreground text-xs font-medium tracking-wider uppercase">Sort by</h2>
            <div
              className="border-border bg-background/60 inline-flex flex-wrap items-center gap-1 rounded-md border p-1 shadow-sm"
              aria-hidden="true"
            >
              {Object.keys(SKIN_SORT_OPTIONS).map(key => (
                <Skeleton key={key} className="h-8 w-24 rounded-md" />
              ))}
            </div>
          </div>

          <div className={MEDIA_GRID}>
            {Array.from({ length: 25 }).map((_, i) => (
              <SkinCardSkeleton key={i} />
            ))}
          </div>

          <div className="flex justify-center">
            <PaginationSkeleton />
          </div>
        </div>
      </PageShell>
    </div>
  );
}
