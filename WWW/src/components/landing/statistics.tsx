"use client";

import { websocketUrl } from "@/common/websocket-url";
import CountUp from "react-countup";
import useWebSocket from "react-use-websocket";

export default function Statistics() {
  const { lastJsonMessage } = useWebSocket(websocketUrl("ws/statistics"), {
    reconnectAttempts: 1000000000,
    reconnectInterval: 1000,
    retryOnError: true,
  });
  const statistics = lastJsonMessage as { playersTracked: number; trackedSkins: number } | null;

  const stats = [
    { label: "Players tracked", value: statistics?.playersTracked },
    { label: "Skins tracked", value: statistics?.trackedSkins },
  ];

  return (
    <dl className="flex flex-wrap items-start justify-center gap-x-12 gap-y-4">
      {stats.map(stat => (
        <div key={stat.label} className="flex flex-col-reverse items-center gap-0.5">
          <dt className="text-muted-foreground text-[13px] font-medium">{stat.label}</dt>
          <dd className="text-foreground text-xl font-medium tabular-nums">
            {stat.value !== undefined ? <CountUp end={stat.value} preserveValue /> : "..."}
          </dd>
        </div>
      ))}
    </dl>
  );
}
