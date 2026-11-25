import { PageHeader, PageShell } from "@/components/page-shell";
import SkinCardSkeleton from "@/components/skeleton/skins/skin-card-skeleton";
import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import { SKIN_SORT_OPTIONS } from "@/components/skins/skin-sort-options";
import { MediaGrid } from "@/components/ui/media-grid";

const DESCRIPTION = "Browse Minecraft player skins sorted by trending, top, or latest.";

export default function SkinsLoading() {
  return (
    <div aria-live="polite" aria-busy="true">
      <PageShell>
        <PageHeader description={DESCRIPTION}>Minecraft Skins</PageHeader>

        <div className="flex w-full flex-col gap-6">
          <div
            className="border-border bg-background/60 inline-flex flex-wrap items-center gap-1 self-start rounded-sm border p-1"
            aria-hidden="true"
          >
            {Object.keys(SKIN_SORT_OPTIONS).map(key => (
              <Skeleton key={key} className="h-8 w-24 rounded-sm" />
            ))}
          </div>

          <MediaGrid>
            {Array.from({ length: 25 }).map((_, i) => (
              <SkinCardSkeleton key={i} />
            ))}
          </MediaGrid>

          <div className="flex justify-center">
            <PaginationSkeleton />
          </div>
        </div>
      </PageShell>
    </div>
  );
}
