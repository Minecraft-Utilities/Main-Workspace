import { cn } from "@/common/utils";
import type { ReactNode } from "react";
import { IdChip } from "./id-chip";
import { StatRow, type StatItem } from "./stat-row";

export interface EntityIdentifier {
  label: string;
  value: string;
}

export interface EntityHeaderProps {
  /** Square game art or icon; rendered at 80px above the centred title on mobile and 64px beside it from sm up. */
  media?: ReactNode;
  mediaClassName?: string;
  title: ReactNode;
  /** A small pill next to the title, such as the IP version. */
  badge?: ReactNode;
  description?: ReactNode;
  identifiers?: EntityIdentifier[];
  stats?: StatItem[];
  actions?: ReactNode;
  className?: string;
}

export function EntityHeader({
  media,
  mediaClassName,
  title,
  badge,
  description,
  identifiers,
  stats,
  actions,
  className,
}: EntityHeaderProps) {
  return (
    <header
      className={cn(
        "flex w-full min-w-0 flex-col items-center gap-5 text-center sm:items-stretch sm:text-left",
        className
      )}
    >
      <div className="flex w-full min-w-0 flex-col items-center gap-3 sm:flex-row sm:gap-4">
        {media ? (
          <div
            className={cn(
              "bg-stage border-border shadow-surface flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-md border sm:size-16",
              mediaClassName
            )}
          >
            {media}
          </div>
        ) : null}

        <div className="flex w-full min-w-0 flex-1 flex-col items-center gap-2 sm:items-start">
          <div className="flex max-w-full min-w-0 flex-wrap items-center justify-center gap-x-3 gap-y-1 sm:justify-start">
            <h1 className="text-foreground max-w-full min-w-0 text-2xl leading-8 font-[650] tracking-[-0.02em] break-words sm:text-[28px] sm:leading-9">
              {title}
            </h1>
            {badge}
          </div>

          {description ? (
            <p className="text-muted-foreground text-[13px] leading-[18px] font-medium">{description}</p>
          ) : null}

          {identifiers && identifiers.length > 0 ? (
            <div className="flex max-w-full flex-wrap items-center justify-center gap-1.5 sm:justify-start">
              {identifiers.map(identifier => (
                <IdChip
                  key={`${identifier.label}-${identifier.value}`}
                  label={identifier.label}
                  value={identifier.value}
                />
              ))}
            </div>
          ) : null}
        </div>

        {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
      </div>

      {stats && stats.length > 0 ? (
        <StatRow stats={stats} className="justify-center text-center sm:justify-start sm:text-left" />
      ) : null}
    </header>
  );
}
