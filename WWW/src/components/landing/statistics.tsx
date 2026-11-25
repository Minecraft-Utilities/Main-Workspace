"use client";

import { websocketUrl } from "@/common/websocket-url";
import { ShirtIcon, UserIcon } from "lucide-react";
import CountUp from "react-countup";
import useWebSocket from "react-use-websocket";
import { Tooltip } from "../ui/tooltip";

export default function Statistics() {
  const { lastJsonMessage } = useWebSocket(websocketUrl("ws/statistics"), {
    reconnectAttempts: 1000000000,
    reconnectInterval: 1000,
    retryOnError: true,
  });
  const statistics = lastJsonMessage as { playersTracked: number; trackedSkins: number } | null;

  return (
    <div className="text-muted-foreground flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm">
      <Tooltip content={<p>The number of Java Minecraft profiles stored in our database.</p>}>
        <span className="flex items-center gap-1.5">
          <UserIcon className="text-primary size-4" aria-hidden />
          <span>Stored Profiles</span>
          <span className="text-foreground font-semibold tabular-nums">
            {statistics ? <CountUp end={statistics.playersTracked} preserveValue /> : "..."}
          </span>
        </span>
      </Tooltip>
      <span className="bg-border hidden h-4 w-px sm:block" aria-hidden />
      <Tooltip content={<p>The number of unique skins stored in our database.</p>}>
        <span className="flex items-center gap-1.5">
          <ShirtIcon className="text-primary size-4" aria-hidden />
          <span>Skin Collection</span>
          <span className="text-foreground font-semibold tabular-nums">
            {statistics ? <CountUp end={statistics.trackedSkins} preserveValue /> : "..."}
          </span>
        </span>
      </Tooltip>
    </div>
  );
}
