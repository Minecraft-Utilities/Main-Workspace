import { isNotFoundError } from "@/common/mc-utils";
import { breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import { capitalize, formatNumberWithCommas } from "@/common/utils";
import { ErrorCard } from "@/components/error-card";
import { JsonLd } from "@/components/json-ld";
import { PageShell } from "@/components/page-shell";
import { ServerDetails } from "@/components/server/server-details";
import ServerDnsRecords from "@/components/server/server-dns-records";
import ServerHeader from "@/components/server/server-header";
import ServerMotd from "@/components/server/server-motd";
import ServerStatus from "@/components/server/server-status";
import { Metadata } from "next";
import { notFound } from "next/navigation";
import { getServer } from "./data";

export const dynamic = "force-dynamic";

export async function generateMetadata(props: PageProps<"/server/[platform]/[hostname]">): Promise<Metadata> {
  const { platform, hostname } = await props.params;
  const { server, error, edition } = await getServer(platform, hostname);

  if (!server) {
    if (isNotFoundError(error)) {
      notFound();
    }
    return buildMetadata({
      title: "Server unavailable",
      description: "This Minecraft server could not be reached. Try again in a moment.",
      path: `/server/${platform.toLowerCase()}/${hostname}`,
      noindex: true,
    });
  }

  const favicon = "favicon" in server ? server.favicon?.url : undefined;
  const players = server.players;
  const serverName = server.registryEntry?.displayName ?? server.hostname;
  const editionLabel = capitalize(edition.toLowerCase());
  const title = `${serverName}: ${editionLabel} Minecraft Server`;
  const description = `${serverName} has ${formatNumberWithCommas(players.online)}/${formatNumberWithCommas(players.max)} players online. View server status, MOTD, and details on MC Utils.`;

  return {
    ...buildMetadata({
      title,
      description,
      path: `/server/${edition.toLowerCase()}/${server.hostname}`,
      ...(favicon ? { image: favicon } : {}),
    }),
    icons: {
      ...(favicon ? { icon: favicon } : {}),
    },
  };
}

export default async function ServerPage({ params }: PageProps<"/server/[platform]/[hostname]">) {
  const { platform, hostname } = await params;
  const { server, error, edition } = await getServer(platform, hostname);

  if (!server) {
    if (isNotFoundError(error)) {
      notFound();
    }
    return (
      <PageShell>
        <ErrorCard
          title="Server unavailable"
          message={error?.message ?? "This Minecraft server could not be reached. Try again in a moment."}
        />
      </PageShell>
    );
  }

  const serverName = server.registryEntry?.displayName ?? server.hostname;
  const jsonLd = breadcrumbJsonLd([
    { name: "Home", path: "/" },
    {
      name: serverName,
      path: `/server/${edition.toLowerCase()}/${server.hostname}`,
    },
  ]);

  return (
    <PageShell>
      <JsonLd id="server-jsonld" data={jsonLd} />
      <ServerHeader server={server} edition={edition} />

      <div className="flex w-full min-w-0 flex-col gap-4">
        <ServerMotd server={server} edition={edition} />
        <ServerStatus server={server} edition={edition} />
        <ServerDetails server={server} edition={edition} />
        <ServerDnsRecords records={server.records} />
      </div>
    </PageShell>
  );
}
