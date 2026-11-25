import { cn } from "@/common/utils";
import type { ReactNode } from "react";

export interface StatItem {
  label: string;
  value: ReactNode;
  /** Absolute value for a relative time, surfaced as a native tooltip. */
  title?: string;
}

export interface StatRowProps {
  stats: StatItem[];
  className?: string;
}

export function StatRow({ stats, className }: StatRowProps) {
  if (stats.length === 0) {
    return null;
  }

  return (
    <dl className={cn("flex flex-wrap gap-x-6 gap-y-3 sm:gap-x-8", className)}>
      {stats.map(stat => (
        <div key={stat.label} className="flex min-w-0 flex-col gap-0.5">
          <dt className="text-muted-foreground text-[13px] leading-[18px] font-medium">{stat.label}</dt>
          <dd className="text-foreground text-lg leading-7 font-medium tabular-nums sm:text-xl" title={stat.title}>
            {stat.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}
