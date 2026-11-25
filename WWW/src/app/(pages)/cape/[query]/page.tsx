import { env } from "@/common/env";
import { capeRenderUrl, mcUtilsApi } from "@/common/mc-utils";
import { BackLink } from "@/components/breadcrumb-link";
import CapePreview from "@/components/cape/cape-preview";
import EntityDetails from "@/components/entity/entity-details";
import EntityPlayers from "@/components/entity/entity-players";
import { ErrorCard } from "@/components/error-card";
import { PageShell, PageTitle } from "@/components/page-shell";
import SimpleLink from "@/components/simple-link";
import { imageUrl } from "mcutils-js-api";
import type { Metadata } from "next";
import Link from "next/link";
import Script from "next/script";
import { cache } from "react";

export const dynamic = "force-dynamic";

const getCape = cache(async (query: string) => {
  const { data: cape } = await mcUtilsApi.cape().fetch(query);
  return cape ?? null;
});

const NOT_FOUND_DESCRIPTION =
  "This Minecraft cape could not be found. Browse all Minecraft capes on MC Utils.";

export async function generateMetadata(props: PageProps<"/cape/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const cape = await getCape(query);

  if (!cape) {
    return {
      title: "Cape not found",
      description: NOT_FOUND_DESCRIPTION,
      openGraph: {
        title: "Cape not found",
        description: NOT_FOUND_DESCRIPTION,
      },
    };
  }

  const capeName = cape.name ?? "Unknown Cape";
  const title = cape.name ? `${cape.name}: Minecraft Cape` : capeName;
  const description = cape.name
    ? `View the ${cape.name} Minecraft cape on MC Utils: preview the design, see current wearers, and explore cape details.`
    : "View this Minecraft cape on MC Utils: preview the design, see current wearers, and explore cape details.";

  return {
    title,
    icons: {
      icon: imageUrl(cape.parts.FRONT, { size: 64 }),
    },
    description,
    openGraph: {
      title,
      description,
      url: `${env.NEXT_PUBLIC_BASE_URL}/cape/${cape.id}`,
      images: [
        {
          url: capeRenderUrl(cape.id),
        },
      ],
    },
  };
}

export default async function CapePage({ params }: PageProps<"/cape/[query]">) {
  const { query } = await params;
  const cape = await getCape(query);

  if (!cape) {
    return (
      <PageShell>
        <BackLink href="/capes" label="Back to capes" />
        <ErrorCard
          title="Cape not found"
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
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "CreativeWork",
    name: capeName,
    image: capeRenderUrl(cape.id),
    url: `${env.NEXT_PUBLIC_BASE_URL}/cape/${cape.id}`,
    description: `Minecraft cape${cape.name ? `: ${cape.name}` : ""}.`,
  };

  return (
    <PageShell>
      <Script
        id="cape-jsonld"
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

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
