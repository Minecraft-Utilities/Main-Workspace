import { KeyValueList, type KeyValueGroup } from "@/components/ui/key-value-list";
import type { BedrockServer, JavaServer, Platform } from "mcutils-js-api";
import TimeAgo from "../time-ago";
import Card, { CardContent, CardHeader } from "../ui/card";

export interface ServerDetailsProps {
  server: JavaServer | BedrockServer;
  edition: Platform;
}

export function ServerDetails({ server, edition }: ServerDetailsProps) {
  const javaServer = server as JavaServer;
  const bedrockServer = server as BedrockServer;

  const connection: KeyValueGroup = {
    title: "Connection",
    items: [
      { label: "Hostname", value: server.hostname, copyValue: server.hostname },
      { label: "IP address", value: server.ip, copyValue: server.ip },
      { label: "Port", value: String(server.port) },
      {
        label: "Version",
        value:
          edition === "JAVA"
            ? `${javaServer.version.protocolName ?? javaServer.version.name} (${javaServer.version.protocol})`
            : bedrockServer.version.name,
      },
      ...(edition === "JAVA" && javaServer.modded
        ? [{ label: "Mods", value: `${javaServer.forgeData?.mods?.length ?? 0} mods` }]
        : []),
      ...(edition === "BEDROCK" && bedrockServer.gamemode?.name
        ? [{ label: "Gamemode", value: bedrockServer.gamemode.name }]
        : []),
      {
        label: "SRV record",
        value: server.records?.some(record => record.type === "SRV") ? "Yes" : "No",
      },
      ...(server.location?.country ? [{ label: "Location", value: server.location.country }] : []),
      {
        label: "Cached",
        value: server.cached ? <TimeAgo date={new Date(server.cachedTime)} /> : "No",
      },
    ],
  };

  const chat: KeyValueGroup = {
    title: "Chat",
    items: [
      {
        label: "Prevents chat reports",
        tooltip:
          "When enabled, the server hides the report option for players by declaring that chat cannot be reported to Mojang.",
        value: javaServer.preventsChatReports ? "Yes" : "No",
      },
      {
        label: "Enforces secure chat",
        tooltip:
          "When enabled, the server only accepts clients that support signed chat, so other players can verify who sent each message.",
        value: javaServer.enforcesSecureChat ? "Yes" : "No",
      },
      {
        label: "Previews chat",
        tooltip:
          "When enabled, the server receives chat messages as they are typed and sends back a styled preview. Messages are transmitted to the server before being sent.",
        value: javaServer.previewsChat ? "Yes" : "No",
      },
    ],
  };

  return (
    <section className="flex w-full min-w-0 flex-col gap-4">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardHeader>Details</CardHeader>
        <CardContent>
          <KeyValueList groups={edition === "JAVA" ? [connection, chat] : [connection]} />
        </CardContent>
      </Card>
    </section>
  );
}
