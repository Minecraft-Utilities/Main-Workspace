import { MEDIA_GRID } from "@/components/media-card";
import { PageShell, PageTitle } from "@/components/page-shell";
import CapeCardSkeleton from "@/components/skeleton/capes/cape-card-skeleton";
import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";

export default function CapesLoading() {
  return (
    <div aria-live="polite" aria-busy="true">
      <PageShell>
        <PageTitle>Minecraft Capes</PageTitle>

        <div className="flex w-full flex-col gap-6">
          <div className={MEDIA_GRID}>
            {Array.from({ length: 25 }).map((_, i) => (
              <CapeCardSkeleton key={i} />
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
