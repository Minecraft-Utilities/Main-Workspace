import { cn, formatNumberWithCommas } from "@/common/utils";
import Card, { CardContent } from "@/components/ui/card";
import type { BedrockServer, JavaServer, Platform } from "mcutils-js-api";

interface ServerStatusProps {
  server: JavaServer | BedrockServer;
  edition: Platform;
}

export default function ServerStatus({ server, edition }: ServerStatusProps) {
  const online = server.players.max > 0;
  const version = edition === "JAVA" ? (server as JavaServer).version : (server as BedrockServer).version;

  return (
    <section className="w-full min-w-0">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardContent className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2.5 text-sm">
          <span
            className={cn(
              "flex items-center gap-1.5 font-semibold",
              online ? "text-emerald-500" : "text-red-500"
            )}
          >
            <span
              className={cn("size-2 shrink-0 rounded-full", online ? "bg-emerald-500" : "bg-red-500")}
              aria-hidden
            />
            {online ? "Online" : "Offline"}
          </span>

          <span className="bg-border h-4 w-px" aria-hidden />

          <span className="text-muted-foreground">
            <span className="text-foreground font-medium">
              {formatNumberWithCommas(server.players.online)}/{formatNumberWithCommas(server.players.max)}
            </span>{" "}
            players online
          </span>

          <span className="bg-border h-4 w-px" aria-hidden />

          <span className="text-muted-foreground">
            <span className="text-foreground font-medium">{version.name}</span>
            {" · protocol "}
            {version.protocol}
          </span>
        </CardContent>
      </Card>
    </section>
  );
}
