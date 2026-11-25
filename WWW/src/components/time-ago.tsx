"use client";

import { timeAgo, timeAgoRounded } from "@/common/time-utils";
import { useSyncExternalStore } from "react";

interface TimeAgoProps {
  date: Date;
  /** Use a single rounded compact unit, e.g. "3m ago". */
  short?: boolean;
  /** Use a single rounded unit with full words, e.g. "6 months ago". */
  rounded?: boolean;
}

export default function TimeAgo({ date, short = false, rounded = false }: TimeAgoProps) {
  const text = useSyncExternalStore(
    onChange => {
      const id = setInterval(onChange, 1000);
      return () => clearInterval(id);
    },
    () => (short || rounded ? timeAgoRounded(date, short) : timeAgo(date)),
    () => ""
  );

  return <>{text}</>;
}
