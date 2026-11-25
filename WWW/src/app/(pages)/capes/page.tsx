import { capeRenderUrl, mcUtilsApi } from "@/common/mc-utils";
import { buildMetadata } from "@/common/seo";
import { EmptyState } from "@/components/empty-state";
import { MediaCard } from "@/components/media-card";
import { PageHeader, PageShell } from "@/components/page-shell";
import { MediaGrid } from "@/components/ui/media-grid";
import type { Metadata } from "next";

export const dynamic = "force-dynamic";

const DESCRIPTION = "Browse every known cape in Minecraft and the players who own it.";

export const metadata: Metadata = buildMetadata({ title: "Minecraft Capes", description: DESCRIPTION, path: "/capes" });

export default async function CapesPage() {
  const { data: capes } = await mcUtilsApi.cape().list();
  const isEmpty = !capes || capes.length === 0;

  return (
    <PageShell>
      <PageHeader description={DESCRIPTION}>Minecraft Capes</PageHeader>

      {isEmpty ? (
        <EmptyState title="No capes found" description="Try again in a moment." />
      ) : (
        <div className="flex w-full flex-col gap-6">
          <MediaGrid>
            {capes.map(cape => (
              <MediaCard
                key={cape.id}
                href={`/cape/${cape.id}`}
                imageUrl={capeRenderUrl(cape.id, 384)}
                alt={cape.name ? `${cape.name} cape` : "Minecraft cape"}
                caption={cape.name ?? "Unknown cape"}
                count={cape.uniqueOwners}
              />
            ))}
          </MediaGrid>
        </div>
      )}
    </PageShell>
  );
}
