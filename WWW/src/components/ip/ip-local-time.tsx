"use client";

import { useSyncExternalStore } from "react";

export function IpLocalTime({ timeZone }: { timeZone: string }) {
  const text = useSyncExternalStore(
    onChange => {
      const id = setInterval(onChange, 1000);
      return () => clearInterval(id);
    },
    () =>
      new Intl.DateTimeFormat(undefined, {
        timeZone,
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        weekday: "short",
      }).format(new Date()),
    () => ""
  );

  return <span className="tabular-nums">{text}</span>;
}
