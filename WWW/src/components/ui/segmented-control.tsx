import { cn } from "@/common/utils";
import Link from "next/link";
import type { ReactNode } from "react";

export interface SegmentedControlItem {
  value: string;
  label: string;
  href: string;
  icon?: ReactNode;
}

export interface SegmentedControlProps {
  items: SegmentedControlItem[];
  value: string;
  label: string;
  className?: string;
}

export function SegmentedControl({ items, value, label, className }: SegmentedControlProps) {
  return (
    <nav
      aria-label={label}
      className={cn(
        "border-border bg-background/60 flex w-full items-center gap-1 rounded-sm border p-1 sm:inline-flex sm:w-auto",
        className
      )}
    >
      {items.map(item => {
        const isSelected = item.value === value;

        return (
          <Link
            key={item.value}
            href={item.href}
            aria-current={isSelected ? "page" : undefined}
            className={cn(
              "inline-flex h-9 flex-1 items-center justify-center gap-1.5 rounded-sm border px-3 sm:h-8 sm:flex-none text-[13px] font-medium transition-colors duration-[120ms] ease-out focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring [&_svg:not([class*='size-'])]:size-4",
              isSelected
                ? "bg-selected border-selected-border text-foreground"
                : "text-muted-foreground hover:bg-control-hover hover:text-foreground border-transparent"
            )}
          >
            {item.icon}
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
