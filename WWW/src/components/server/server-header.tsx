import type { BedrockServer, JavaServer, Platform } from "mcutils-js-api";
import Image from "next/image";

interface ServerHeaderProps {
  server: JavaServer | BedrockServer;
  edition: Platform;
}

export default function ServerHeader({ server, edition }: ServerHeaderProps) {
  return (
    <header className="flex min-w-0 flex-1 flex-col items-center gap-4">
      <div className="flex flex-col items-center gap-4 md:flex-row">
        {edition === "JAVA" && (server as JavaServer).favicon?.base64 && (
          <Image
            src={(server as JavaServer).favicon!.base64!}
            alt={`${server.hostname} favicon`}
            width={64}
            height={64}
            className="rounded-md"
            priority
            fetchPriority="high"
          />
        )}

        <h1 className="text-foreground min-w-0 text-center text-2xl font-bold tracking-tight wrap-break-word md:text-4xl">
          {server.registryEntry?.displayName ?? server.hostname}
        </h1>
      </div>
    </header>
  );
}
