import { mcUtilsApi } from "@/common/mc-utils";
import { buildMetadata } from "@/common/seo";
import { EmptyState } from "@/components/empty-state";
import { MediaCard } from "@/components/media-card";
import { PageHeader, PageShell } from "@/components/page-shell";
import { isSkinSort, SKIN_SORT_OPTIONS } from "@/components/skins/skin-sort-options";
import { MediaGrid } from "@/components/ui/media-grid";
import Pagination from "@/components/ui/pagination";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { imageUrl, type SkinSort } from "mcutils-js-api";
import type { Metadata } from "next";

export const dynamic = "force-dynamic";

const DESCRIPTION = "Browse Minecraft player skins sorted by trending, top, or latest.";

function parseListingQuery(query: string[] | undefined) {
  const rawFirst = query?.[0];
  const rawSort = rawFirst?.toUpperCase();
  const hasSort = isSkinSort(rawSort);
  const sort = hasSort ? rawSort : "TRENDING";
  const parsedPage = Number(hasSort ? (query!.length >= 2 ? query![1] : "1") : (rawFirst ?? "1"));

  return {
    sort,
    hasSort,
    page: Number.isInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1,
  };
}

const sortItems = (Object.keys(SKIN_SORT_OPTIONS) as SkinSort[]).map(key => ({
  value: key,
  label: SKIN_SORT_OPTIONS[key].label,
  icon: SKIN_SORT_OPTIONS[key].icon,
  href: `/skins/${key.toLowerCase()}`,
}));

export async function generateMetadata(props: PageProps<"/skins/[[...query]]">): Promise<Metadata> {
  const { query } = await props.params;
  const { sort, hasSort, page } = parseListingQuery(query);
  const sortSegment = hasSort ? `/${sort.toLowerCase()}` : "";
  const path = `/skins${sortSegment}${page > 1 ? `/${page}` : ""}`;

  return buildMetadata({ title: "Minecraft Skins", description: DESCRIPTION, path });
}

export default async function SkinsPage({ params }: PageProps<"/skins/[[...query]]">) {
  const { query } = await params;
  const { sort, page } = parseListingQuery(query);

  const { data: skins } = await mcUtilsApi.skin().list(page, sort);
  const isEmpty = !skins || skins.items.length === 0;

  return (
    <PageShell>
      <PageHeader description={DESCRIPTION}>Minecraft Skins</PageHeader>

      {isEmpty ? (
        <EmptyState title="No skins found" description="Try again in a moment." />
      ) : (
        <div className="flex w-full flex-col gap-6">
          <SegmentedControl items={sortItems} value={sort} label="Sort skins" className="sm:self-start" />

          <MediaGrid>
            {skins.items.map((skin, index) => (
              <div key={skin.id} className="relative">
                <MediaCard
                  href={`/skin/${skin.id}`}
                  imageUrl={imageUrl(skin.parts.FULLBODY_ISO_FRONT, { size: 384 })}
                  alt={`Minecraft skin ${skin.id}`}
                  count={skin.uniqueOwners}
                />
                {sort === "TOP" ? (
                  <span className="bg-highlight text-background pointer-events-none absolute top-2 left-2 rounded-sm px-1.5 py-0.5 text-[11px] leading-4 font-semibold tabular-nums">
                    #{index + 1 + (page - 1) * skins.itemsPerPage}
                  </span>
                ) : null}
              </div>
            ))}
          </MediaGrid>

          <div className="flex justify-center">
            <Pagination
              page={page}
              totalItems={skins.totalItems}
              itemsPerPage={skins.itemsPerPage}
              basePath={`/skins/${sort.toLowerCase()}`}
            />
          </div>
        </div>
      )}
    </PageShell>
  );
}
