import { PageHeader, PageShell } from "@/components/page-shell";
import CapeCardSkeleton from "@/components/skeleton/capes/cape-card-skeleton";
import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import { MediaGrid } from "@/components/ui/media-grid";

const DESCRIPTION = "Browse every known cape in Minecraft and the players who own it.";

export default function CapesLoading() {
  return (
    <div aria-live="polite" aria-busy="true">
      <PageShell>
        <PageHeader description={DESCRIPTION}>Minecraft Capes</PageHeader>

        <div className="flex w-full flex-col gap-6">
          <MediaGrid>
            {Array.from({ length: 25 }).map((_, i) => (
              <CapeCardSkeleton key={i} />
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
