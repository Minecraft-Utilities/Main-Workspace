import { capeRenderUrl, mcUtilsApi } from "@/common/mc-utils";
import { buildMetadata } from "@/common/seo";
import { EmptyState } from "@/components/empty-state";
import { MediaCard } from "@/components/media-card";
import { PageHeader, PageShell } from "@/components/page-shell";
import { MediaGrid } from "@/components/ui/media-grid";
import Pagination from "@/components/ui/pagination";
import type { Metadata } from "next";

export const dynamic = "force-dynamic";

const DESCRIPTION = "Browse every known cape in Minecraft and the players who own it.";

function parsePage(query: string[] | undefined): number {
  const parsed = Number(query?.[0] ?? "1");
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

export async function generateMetadata(props: PageProps<"/capes/[[...query]]">): Promise<Metadata> {
  const { query } = await props.params;
  const page = parsePage(query);

  return buildMetadata({
    title: "Minecraft Capes",
    description: DESCRIPTION,
    path: page > 1 ? `/capes/${page}` : "/capes",
  });
}

export default async function CapesPage({ params }: PageProps<"/capes/[[...query]]">) {
  const { query } = await params;
  const page = parsePage(query);

  const { data: capes } = await mcUtilsApi.cape().list(page);
  const isEmpty = !capes || capes.items.length === 0;

  return (
    <PageShell>
      <PageHeader description={DESCRIPTION}>Minecraft Capes</PageHeader>

      {isEmpty ? (
        <EmptyState title="No capes found" description="Try again in a moment." />
      ) : (
        <div className="flex w-full flex-col gap-6">
          <MediaGrid>
            {capes.items.map(cape => (
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
