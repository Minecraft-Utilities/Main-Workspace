import { capitalize, formatNumberWithCommas } from "@/common/utils";
import { BreakableText } from "@/components/breakable-text";
import { EntityHeader } from "@/components/ui/entity-header";
import { Server } from "lucide-react";
import type { BedrockServer, JavaServer, Platform } from "mcutils-js-api";
import Image from "next/image";

interface ServerHeaderProps {
  server: JavaServer | BedrockServer;
  edition: Platform;
}

export default function ServerHeader({ server, edition }: ServerHeaderProps) {
  const favicon = edition === "JAVA" ? (server as JavaServer).favicon?.base64 : undefined;
  const name = server.registryEntry?.displayName ?? server.hostname;
  const version = edition === "JAVA" ? (server as JavaServer).version : (server as BedrockServer).version;

  const identifiers = [{ label: "Hostname", value: server.hostname }];
  if (server.ip && server.ip !== server.hostname) {
    identifiers.push({ label: "IP address", value: server.ip });
  }

  return (
    <EntityHeader
      media={
        favicon ? (
          <Image
            src={favicon}
            alt={`${name} server icon`}
            width={64}
            height={64}
            className="pixelated size-full object-cover"
            priority
            fetchPriority="high"
          />
        ) : (
          <Server className="text-text-faint size-6 sm:size-8" aria-hidden />
        )
      }
      title={<BreakableText text={name} />}
      description={`${capitalize(edition.toLowerCase())} edition`}
      identifiers={identifiers}
      stats={[
        {
          label: "Players online",
          value: `${formatNumberWithCommas(server.players.online)}/${formatNumberWithCommas(server.players.max)}`,
        },
        { label: "Version", value: version.name },
      ]}
    />
  );
}
