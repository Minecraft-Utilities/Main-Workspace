import { PageShell } from "@/components/page-shell";
import EntityPlayersSkeleton from "@/components/skeleton/entity/entity-players-skeleton";
import PreviewSkeleton from "@/components/skeleton/entity/preview-skeleton";
import SkinHeadCommandsSkeleton from "@/components/skeleton/skin/skin-head-commands-skeleton";
import SkinHeaderSkeleton from "@/components/skeleton/skin/skin-header-skeleton";

export default function SkinLoading() {
  return (
    <PageShell>
      <div className="flex w-full min-w-0 flex-col gap-10" aria-live="polite" aria-busy="true">
        <SkinHeaderSkeleton />

        <div className="flex w-full min-w-0 flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
          <aside className="flex w-full flex-col gap-4 lg:w-90 lg:shrink-0">
            <PreviewSkeleton />
            <SkinHeadCommandsSkeleton />
          </aside>

          <div className="flex min-w-0 flex-1 flex-col gap-4">
            <EntityPlayersSkeleton />
          </div>
        </div>
      </div>
    </PageShell>
  );
}
