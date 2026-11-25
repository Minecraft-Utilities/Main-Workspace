import { capitalize, formatNumberWithCommas } from "@/common/utils";
import { ErrorCard } from "@/components/error-card";
import { PageShell } from "@/components/page-shell";
import { ServerDetails } from "@/components/server/server-details";
import ServerDnsRecords from "@/components/server/server-dns-records";
import ServerHeader from "@/components/server/server-header";
import ServerMotd from "@/components/server/server-motd";
import ServerStatus from "@/components/server/server-status";
import { Metadata } from "next";
import { getServer } from "./data";

export const dynamic = "force-dynamic";

export async function generateMetadata(props: PageProps<"/server/[platform]/[hostname]">): Promise<Metadata> {
  const { platform, hostname } = await props.params;
  const { server, error, edition } = await getServer(platform, hostname);

  if (error || !server) {
    return {
      title: "Server not found",
      description: "This Minecraft server could not be found or is currently offline.",
      openGraph: {
        title: "Server not found",
        description: "This Minecraft server could not be found or is currently offline.",
      },
    };
  }
  const favicon = "favicon" in server ? server.favicon?.url : undefined;
  const players = server.players;
  const serverName = server.registryEntry?.displayName ?? server.hostname;
  const editionLabel = capitalize(edition.toLowerCase());

  return {
    title: `${serverName}: ${editionLabel} Minecraft Server`,
    description: `${serverName} has ${formatNumberWithCommas(players.online)}/${formatNumberWithCommas(players.max)} players online. View server status, MOTD, and details on MC Utils.`,
    icons: {
      ...(favicon ? { icon: favicon } : {}),
    },
    openGraph: {
      title: `${serverName}: ${editionLabel} Minecraft Server`,
      description: `${serverName} has ${formatNumberWithCommas(players.online)}/${formatNumberWithCommas(players.max)} players online. View server status, MOTD, and details on MC Utils.`,
      ...(favicon ? { images: [{ url: favicon }] } : {}),
    },
  };
}

export default async function ServerPage({ params }: PageProps<"/server/[platform]/[hostname]">) {
  const { platform, hostname } = await params;
  const { server, error, edition } = await getServer(platform, hostname);

  if (!server) {
    return (
      <PageShell>
        <ErrorCard
          title="Server not found"
          message={error?.message ?? "This Minecraft server could not be found or is currently offline."}
        />
      </PageShell>
    );
  }

  return (
    <PageShell>
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
