"use client";

import { timeAgo } from "@/common/time-utils";
import { useSyncExternalStore } from "react";

interface TimeAgoProps {
  date: Date;
}

export default function TimeAgo({ date }: TimeAgoProps) {
  const text = useSyncExternalStore(
    onChange => {
      const id = setInterval(onChange, 1000);
      return () => clearInterval(id);
    },
    () => timeAgo(date),
    () => ""
  );

  return <>{text}</>;
}
