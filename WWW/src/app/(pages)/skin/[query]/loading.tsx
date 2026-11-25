import SkinHeadCommandsSkeleton from "@/components/skeleton/skin/skin-head-commands-skeleton";
import SkinHeaderSkeleton from "@/components/skeleton/skin/skin-header-skeleton";
import SkinPlayersSkeleton from "@/components/skeleton/skin/skin-players-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function SkinLoading() {
  return (
    <div
      className="mt-10 flex w-full flex-col items-center justify-center gap-10"
      aria-live="polite"
      aria-busy="true"
    >
      <SkinHeaderSkeleton />

      <div className="flex w-full max-w-[980px] flex-col gap-4 lg:flex-row">
        {/* Left: preview + details */}
        <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
          <Card className="h-fit w-full">
            <CardContent className="flex items-center justify-center">
              <div className="bg-muted h-96 w-full rounded-xl" />
            </CardContent>
          </Card>

          <Card className="h-fit w-full overflow-hidden p-0">
            <CardHeader>Details</CardHeader>
            <CardContent className="flex flex-col gap-3">
              <Skeleton className="h-5 w-full rounded" />
              <Skeleton className="h-5 w-2/3 rounded" />
              <Skeleton className="h-5 w-3/4 rounded" />
              <Skeleton className="h-5 w-1/2 rounded" />
            </CardContent>
          </Card>
        </div>

        {/* Right: owners + commands */}
        <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
          <SkinPlayersSkeleton />
          <SkinHeadCommandsSkeleton />
        </div>
      </div>
    </div>
  );
}
