import { PageShell } from "@/components/page-shell";
import ServerDetailsSkeleton from "@/components/skeleton/server/server-details-skeleton";
import ServerDnsRecordsSkeleton from "@/components/skeleton/server/server-dns-records-skeleton";
import ServerHeaderSkeleton from "@/components/skeleton/server/server-header-skeleton";
import ServerMotdSkeleton from "@/components/skeleton/server/server-motd-skeleton";

export default function ServerLoading() {
  return (
    <PageShell>
      <div className="flex w-full min-w-0 flex-col gap-10" aria-live="polite" aria-busy="true">
        <ServerHeaderSkeleton />

        <div className="flex w-full min-w-0 flex-col gap-8">
          <ServerMotdSkeleton />
          <ServerDetailsSkeleton />
          <ServerDnsRecordsSkeleton />
        </div>
      </div>
    </PageShell>
  );
}
