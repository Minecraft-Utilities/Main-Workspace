import { env } from "@/common/env";
import { capeRenderUrl, mcUtilsApi } from "@/common/mc-utils";
import { EmptyState } from "@/components/empty-state";
import { MEDIA_GRID, MediaCard } from "@/components/media-card";
import { PageShell, PageTitle } from "@/components/page-shell";
import Pagination from "@/components/ui/pagination";
import type { Metadata } from "next";

export const dynamic = "force-dynamic";

const DESCRIPTION = "Browse every known cape in Minecraft and the players who own it.";

export const metadata: Metadata = {
  title: "Minecraft Capes",
  description: DESCRIPTION,
  openGraph: {
    title: "Minecraft Capes",
    description: DESCRIPTION,
    url: `${env.NEXT_PUBLIC_BASE_URL}/capes`,
  },
};

export default async function CapesPage({ params }: PageProps<"/capes/[[...query]]">) {
  const { query } = await params;
  const page = Number(query?.[0] ?? "1");

  const { data: capes } = await mcUtilsApi.cape().list(page);
  const isEmpty = !capes || capes.items.length === 0;

  return (
    <PageShell>
      <PageTitle>Minecraft Capes</PageTitle>

      {isEmpty ? (
        <EmptyState title="No capes found" description="Try again in a moment." />
      ) : (
        <div className="flex w-full flex-col gap-6">
          <div className={MEDIA_GRID}>
            {capes.items.map(cape => (
              <MediaCard
                key={cape.id}
                href={`/cape/${cape.id}`}
                imageUrl={capeRenderUrl(cape.id, 384)}
                alt={cape.name ?? "Minecraft Cape"}
                caption={cape.name ?? "Unknown Cape"}
                count={cape.uniqueOwners}
              />
            ))}
          </div>

          <div className="flex justify-center">
            <Pagination
              page={page}
              totalItems={capes.totalItems}
              itemsPerPage={capes.itemsPerPage}
              basePath="/capes"
            />
          </div>
        </div>
      )}
    </PageShell>
  );
}
