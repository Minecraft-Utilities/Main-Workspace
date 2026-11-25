import { env } from "@/common/env";
import { mcUtilsApi } from "@/common/mc-utils";
import { EmptyState } from "@/components/empty-state";
import { MEDIA_GRID, MediaCard } from "@/components/media-card";
import { PageShell, PageTitle } from "@/components/page-shell";
import { isSkinSort, SKIN_SORT_OPTIONS } from "@/components/skins/skin-sort-options";
import { Button } from "@/components/ui/button";
import Pagination from "@/components/ui/pagination";
import type { Metadata } from "next";
import Link from "next/link";

export const dynamic = "force-dynamic";

const DESCRIPTION = "Browse Minecraft player skins sorted by trending, top, or latest.";

export const metadata: Metadata = {
  title: "Minecraft Skins",
  description: DESCRIPTION,
  openGraph: {
    title: "Minecraft Skins",
    description: DESCRIPTION,
    url: `${env.NEXT_PUBLIC_BASE_URL}/skins`,
  },
};

export default async function SkinsPage({ params }: PageProps<"/skins/[[...query]]">) {
  const { query } = await params;

  const rawFirst = query?.[0];
  const rawSort = rawFirst?.toUpperCase();
  const hasSort = isSkinSort(rawSort);
  const sort = hasSort ? rawSort : "TRENDING";
  const page = Number(hasSort ? (query!.length >= 2 ? query![1] : "1") : (rawFirst ?? "1"));

  const { data: skins } = await mcUtilsApi.skin().list(page, sort);
  const isEmpty = !skins || skins.items.length === 0;

  return (
    <PageShell>
      <PageTitle>Minecraft Skins</PageTitle>

      {isEmpty ? (
        <EmptyState title="No skins found" description="Try again in a moment." />
      ) : (
        <div className="flex w-full flex-col gap-6">
          <div className="flex flex-col items-start gap-2">
            <h2
              id="skin-sort-label"
              className="text-muted-foreground text-xs font-medium tracking-wider uppercase"
            >
              Sort by
            </h2>
            <div
              role="group"
              aria-labelledby="skin-sort-label"
              className="border-border bg-background/60 inline-flex flex-wrap items-center gap-1 rounded-md border p-1 shadow-sm"
            >
              {Object.entries(SKIN_SORT_OPTIONS).map(([key, option]) => (
                <Button
                  key={key}
                  asChild
                  size="sm"
                  variant={sort === key ? "default" : "ghost"}
                  aria-current={sort === key ? true : undefined}
                >
                  <Link href={`/skins/${key.toLowerCase()}`}>
                    {option.icon}
                    {option.label}
                  </Link>
                </Button>
              ))}
            </div>
          </div>

          <div className={MEDIA_GRID}>
            {skins.items.map(skin => (
              <MediaCard
                key={skin.id}
                href={`/skin/${skin.id}`}
                imageUrl={skin.parts.FULLBODY_ISO_FRONT}
                alt="Minecraft Skin"
                count={skin.uniqueOwners}
              />
            ))}
          </div>

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
