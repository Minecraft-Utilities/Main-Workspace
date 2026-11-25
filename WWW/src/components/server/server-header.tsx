import { PageTitle } from "@/components/page-shell";
import type { BedrockServer, JavaServer, Platform } from "mcutils-js-api";
import Image from "next/image";

interface ServerHeaderProps {
  server: JavaServer | BedrockServer;
  edition: Platform;
}

export default function ServerHeader({ server, edition }: ServerHeaderProps) {
  const favicon = edition === "JAVA" ? (server as JavaServer).favicon?.base64 : undefined;
  const name = server.registryEntry?.displayName ?? server.hostname;

  return (
    <div className="flex w-full min-w-0 items-center gap-3 sm:gap-4">
      {favicon && (
        <Image
          src={favicon}
          alt=""
          width={64}
          height={64}
          className="size-14 shrink-0 rounded-md"
          priority
          fetchPriority="high"
        />
      )}
      <PageTitle className="min-w-0 flex-1">
        <span className="block break-all">{name}</span>
      </PageTitle>
    </div>
  );
}
