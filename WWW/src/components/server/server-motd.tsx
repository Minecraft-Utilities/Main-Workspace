import type { BedrockServer, JavaServer, Platform } from "mcutils-js-api";
import Image from "next/image";

interface ServerMotdProps {
  server: JavaServer | BedrockServer;
  edition: Platform;
}

export default function ServerMotd({ server, edition }: ServerMotdProps) {
  const preview = edition === "JAVA" ? (server as JavaServer).motd?.preview : undefined;

  if (!preview) {
    return null;
  }

  return (
    <section className="bg-stage border-border shadow-surface w-full overflow-hidden rounded-lg border bg-[radial-gradient(circle_at_50%_40%,var(--stage-highlight),var(--stage)_75%)]">
      <Image
        src={preview}
        alt={`${server.hostname} MOTD preview`}
        width={768}
        height={87}
        className="pixelated h-auto w-full object-contain"
        priority
      />
    </section>
  );
}
