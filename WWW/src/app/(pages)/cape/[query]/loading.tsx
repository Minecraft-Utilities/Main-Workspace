import { PageShell } from "@/components/page-shell";
import CapeHeaderSkeleton from "@/components/skeleton/cape/cape-header-skeleton";
import EntityDetailsSkeleton from "@/components/skeleton/entity/entity-details-skeleton";
import EntityPlayersSkeleton from "@/components/skeleton/entity/entity-players-skeleton";
import PreviewSkeleton from "@/components/skeleton/entity/preview-skeleton";

export default function CapeLoading() {
  return (
    <PageShell>
      <div className="flex w-full flex-col items-center gap-10" aria-live="polite" aria-busy="true">
        <CapeHeaderSkeleton />

        <div className="flex w-full flex-col gap-4 lg:flex-row">
          {/* Left: preview + details */}
          <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
            <PreviewSkeleton />
            <EntityDetailsSkeleton />
          </div>

          {/* Right: wearers */}
          <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
            <EntityPlayersSkeleton />
          </div>
        </div>
      </div>
    </PageShell>
  );
}
