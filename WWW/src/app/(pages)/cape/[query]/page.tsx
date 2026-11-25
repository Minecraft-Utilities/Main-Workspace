import { capeRenderUrl, isNotFoundError, mcUtilsApi } from "@/common/mc-utils";
import { absoluteUrl, breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import { BackLink } from "@/components/breadcrumb-link";
import CapePreview from "@/components/cape/cape-preview";
import EntityDetails from "@/components/entity/entity-details";
import EntityPlayers from "@/components/entity/entity-players";
import { ErrorCard } from "@/components/error-card";
import { JsonLd } from "@/components/json-ld";
import { PageShell, PageTitle } from "@/components/page-shell";
import SimpleLink from "@/components/simple-link";
import { imageUrl } from "mcutils-js-api";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

export const dynamic = "force-dynamic";

const getCape = cache(async (query: string) => {
  if (!/^\d+$/.test(query)) {
    notFound();
  }
  const { data: cape, error } = await mcUtilsApi.cape().fetch(query);
  return { cape, error };
});

export async function generateMetadata(props: PageProps<"/cape/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { cape, error } = await getCape(query);

  if (!cape) {
    if (isNotFoundError(error)) {
      notFound();
    }
    return buildMetadata({
      title: "Cape unavailable",
      description: "This Minecraft cape is temporarily unavailable. Try again in a moment.",
      path: `/cape/${query}`,
      noindex: true,
    });
  }

  const capeName = cape.name ?? "Unknown Cape";
  const title = cape.name ? `${cape.name}: Minecraft Cape` : capeName;
  const description = cape.name
    ? `View the ${cape.name} Minecraft cape on MC Utils: preview the design, see current wearers, and explore cape details.`
    : "View this Minecraft cape on MC Utils: preview the design, see current wearers, and explore cape details.";

  return {
    ...buildMetadata({
      title,
      description,
      path: `/cape/${cape.id}`,
      image: capeRenderUrl(cape.id),
    }),
    icons: {
      icon: imageUrl(cape.parts.FRONT, { size: 64 }),
    },
  };
}

export default async function CapePage({ params }: PageProps<"/cape/[query]">) {
  const { query } = await params;
  const { cape, error } = await getCape(query);

  if (!cape) {
    if (isNotFoundError(error)) {
      notFound();
    }
    return (
      <PageShell>
        <BackLink href="/capes" label="Back to capes" />
        <ErrorCard
          title="Cape unavailable"
          message={
            <>
              We couldn&apos;t find a cape matching &quot;{query}&quot;.{" "}
              <Link href="/capes" className="underline underline-offset-4">
                Browse all capes
              </Link>
              .
            </>
          }
        />
      </PageShell>
    );
  }

  const capeName = cape.name ?? "Unknown Cape";
  const jsonLd = [
    {
      "@context": "https://schema.org",
      "@type": "CreativeWork",
      name: capeName,
      image: capeRenderUrl(cape.id),
      url: absoluteUrl(`/cape/${cape.id}`),
      description: `Minecraft cape${cape.name ? `: ${cape.name}` : ""}.`,
    },
    breadcrumbJsonLd([
      { name: "Home", path: "/" },
      { name: "Minecraft Capes", path: "/capes" },
      { name: capeName, path: `/cape/${cape.id}` },
    ]),
  ];

  return (
    <PageShell>
      <JsonLd id="cape-jsonld" data={jsonLd} />

      <div className="flex w-full flex-col gap-2">
        <BackLink href="/capes" label="Back to capes" />
        <PageTitle
          description={
            cape.firstSeenUsing ? (
              <SimpleLink
                href={`/player/${cape.firstSeenUsing}`}
                className="underline-offset-4 hover:underline"
              >
                View {cape.firstSeenUsing}&apos;s player profile
              </SimpleLink>
            ) : undefined
          }
        >
          {capeName}
        </PageTitle>
      </div>

      <div className="flex w-full flex-col gap-4 lg:flex-row">
        {/* Left: preview + details */}
        <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
          <CapePreview cape={cape} />
          <EntityDetails kind="cape" entity={cape} />
        </div>

        {/* Right: wearers */}
        <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
          <EntityPlayers
            kind="cape"
            accounts={cape.accountsSeenOwning ?? []}
            currentOwners={cape.currentOwners ?? 0}
          />
        </div>
      </div>
    </PageShell>
  );
}
