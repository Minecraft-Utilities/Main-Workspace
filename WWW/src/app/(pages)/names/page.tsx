import { env } from "@/common/env";
import { mcUtilsApi } from "@/common/mc-utils";
import { EmptyState } from "@/components/empty-state";
import { ErrorCard } from "@/components/error-card";
import NameChanges from "@/components/names/name-changes";
import { PageShell, PageTitle } from "@/components/page-shell";
import type { Metadata } from "next";

export const dynamic = "force-dynamic";

const DESCRIPTION = "Recent Minecraft username changes across all players tracked by MC Utils.";

export const metadata: Metadata = {
  title: "Recent Name Changes",
  description: DESCRIPTION,
  openGraph: {
    title: "Recent Name Changes",
    description: DESCRIPTION,
    url: `${env.NEXT_PUBLIC_BASE_URL}/names`,
  },
};

export default async function NamesPage() {
  const { data: nameChanges, error } = await mcUtilsApi.player().nameChanges();

  return (
    <PageShell>
      <PageTitle>Recent Name Changes</PageTitle>

      {error ? (
        <ErrorCard title="Couldn't load name changes" message={error.message} />
      ) : nameChanges && nameChanges.length > 0 ? (
        <NameChanges nameChanges={nameChanges} />
      ) : (
        <EmptyState
          title="No name changes yet"
          description="Minecraft username changes will appear here as players are tracked."
        />
      )}
    </PageShell>
  );
}
