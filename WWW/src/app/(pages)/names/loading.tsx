import { TOTAL_NAME_CHANGES_TO_SHOW } from "@/components/names/constants";
import { PageShell, PageTitle } from "@/components/page-shell";
import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function NamesLoading() {
  return (
    <div aria-live="polite" aria-busy="true">
      <PageShell>
        <PageTitle>Recent Name Changes</PageTitle>

        <Card className="w-full overflow-hidden p-0">
          <CardHeader className="flex items-center justify-between gap-2">
            <span>Name History</span>
            <span className="text-muted-foreground tracking-normal normal-case">
              Showing latest {TOTAL_NAME_CHANGES_TO_SHOW}
            </span>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-border/60 divide-y">
              {Array.from({ length: TOTAL_NAME_CHANGES_TO_SHOW }).map((_, i) => (
                <div key={i} className="flex items-center gap-2 px-3 py-2">
                  <Skeleton className="size-[22px] shrink-0 rounded-sm" />

                  <div className="flex min-w-0 flex-1 items-center gap-1.5 text-sm">
                    <Skeleton className="h-4 w-24 rounded-md" />
                    <Skeleton className="h-3 w-3 rounded-full" />
                    <Skeleton className="h-4 min-w-0 flex-1 rounded-md" />
                  </div>

                  <Skeleton className="h-3 w-14 rounded-md" />
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </PageShell>
    </div>
  );
}
