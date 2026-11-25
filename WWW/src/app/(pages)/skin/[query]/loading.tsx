import { PageShell } from "@/components/page-shell";
import EntityDetailsSkeleton from "@/components/skeleton/entity/entity-details-skeleton";
import EntityPlayersSkeleton from "@/components/skeleton/entity/entity-players-skeleton";
import PreviewSkeleton from "@/components/skeleton/entity/preview-skeleton";
import SkinHeadCommandsSkeleton from "@/components/skeleton/skin/skin-head-commands-skeleton";
import SkinHeaderSkeleton from "@/components/skeleton/skin/skin-header-skeleton";

export default function SkinLoading() {
  return (
    <PageShell>
      <div className="flex w-full flex-col items-center gap-10" aria-live="polite" aria-busy="true">
        <SkinHeaderSkeleton />

        <div className="flex w-full flex-col gap-4 lg:flex-row">
          {/* Left: preview + details */}
          <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
            <PreviewSkeleton />
            <EntityDetailsSkeleton />
          </div>

          {/* Right: users + commands */}
          <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
            <EntityPlayersSkeleton />
            <SkinHeadCommandsSkeleton />
          </div>
        </div>
      </div>
    </PageShell>
  );
}
