import { cn } from "@/common/utils";
import { CopyButton } from "@/components/copy-button";
import SimpleTooltip from "@/components/simple-tooltip";
import { Info } from "lucide-react";
import type { ReactNode } from "react";

export interface KeyValueItem {
  label: string;
  value: ReactNode;
  /** Shows a row copy button, hidden until hover or focus, always visible on touch. */
  copyValue?: string;
  copyLabel?: string;
  tooltip?: string;
}

export interface KeyValueGroup {
  /** Optional subheading above the group's rows, in sentence case. */
  title?: string;
  items: KeyValueItem[];
}

export interface KeyValueListProps {
  groups: KeyValueGroup[];
  className?: string;
}

export function KeyValueList({ groups, className }: KeyValueListProps) {
  return (
    <div className={cn("flex flex-col gap-5", className)}>
      {groups.map((group, groupIndex) =>
        group.items.length === 0 ? null : (
          <section key={group.title ?? groupIndex} className="flex flex-col gap-2">
            {group.title ? (
              <h3 className="text-muted-foreground text-[13px] leading-[18px] font-semibold">
                {group.title}
              </h3>
            ) : null}
            <dl className="grid grid-cols-1 gap-x-6 sm:grid-cols-[max-content_minmax(0,1fr)] sm:items-center">
              {group.items.map(item => {
                const copyLabel = item.copyLabel ?? item.label;

                return (
                  <div
                    key={item.label}
                    className="group/row flex min-w-0 flex-col gap-y-0.5 py-2 first:pt-0 last:pb-0 sm:col-span-2 sm:grid sm:grid-cols-subgrid sm:items-center sm:gap-y-0 sm:py-1.5"
                  >
                    <dt className="text-muted-foreground flex items-center gap-1.5 text-[13px] leading-[18px] font-medium">
                      {item.label}
                      {item.tooltip ? (
                        <SimpleTooltip display={item.tooltip} side="right" className="w-auto">
                          <Info className="text-text-faint size-3.5 shrink-0" aria-hidden />
                        </SimpleTooltip>
                      ) : null}
                    </dt>
                    <dd className="text-foreground flex min-w-0 items-center gap-1.5 text-[13px] leading-5 tabular-nums">
                      <span className="min-w-0 flex-1 wrap-anywhere sm:flex-none">{item.value}</span>
                      {item.copyValue ? (
                        <SimpleTooltip display={`Copy ${copyLabel}`} side="right" className="w-auto">
                          <CopyButton
                            value={item.copyValue}
                            label={`Copy ${copyLabel}`}
                            variant="icon"
                            className="-my-2.5 size-10 shrink-0 opacity-0 md:my-0 md:size-7 transition-opacity duration-[120ms] group-focus-within/row:opacity-100 group-hover/row:opacity-100 [@media(hover:none)]:opacity-100"
                          />
                        </SimpleTooltip>
                      ) : null}
                    </dd>
                  </div>
                );
              })}
            </dl>
          </section>
        )
      )}
    </div>
  );
}
