import { cn, formatNumberWithCommas } from "@/common/utils";
import SimpleTooltip from "@/components/simple-tooltip";
import { UserIcon } from "lucide-react";

export type OwnerCountVariant = "default" | "overlay";

export interface OwnerCountProps {
  count: number;
  name?: string;
  /** "overlay" is for gradient overlays on media cards. */
  variant?: OwnerCountVariant;
  /** Set when an enclosing link already announces the count. */
  ariaHidden?: boolean;
  className?: string;
}

export default function OwnerCount({
  count,
  name,
  variant = "default",
  ariaHidden = false,
  className,
}: OwnerCountProps) {
  const formatted = formatNumberWithCommas(count);
  const label = name ? `${formatted} ${name}` : formatted;

  const content = (
    <div
      className={cn("flex items-center gap-1", className)}
      role={ariaHidden ? undefined : "img"}
      aria-label={ariaHidden ? undefined : label}
      aria-hidden={ariaHidden || undefined}
    >
      <UserIcon className="text-primary size-3.5" aria-hidden />
      <span className={cn("text-xs", variant === "overlay" ? "text-white/60" : "text-muted-foreground")}>
        {formatted}
        {name ? ` ${name}` : ""}
      </span>
    </div>
  );

  if (ariaHidden) {
    return content;
  }

  return (
    <SimpleTooltip display={label} className="justify-start">
      {content}
    </SimpleTooltip>
  );
}
