import { cn } from "@/common/utils";
import { CircleAlert, Inbox, SearchX, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export type StateBlockVariant = "empty" | "not-found" | "error";

export interface StateBlockProps {
  variant?: StateBlockVariant;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  /** Overrides the variant's default 20px icon. */
  icon?: LucideIcon;
  className?: string;
}

const VARIANTS: Record<StateBlockVariant, { icon: LucideIcon; tint: string }> = {
  empty: { icon: Inbox, tint: "text-muted-foreground" },
  "not-found": { icon: SearchX, tint: "text-warning" },
  error: { icon: CircleAlert, tint: "text-danger" },
};

export function StateBlock({
  variant = "empty",
  title,
  description,
  action,
  icon,
  className,
}: StateBlockProps) {
  const { icon: DefaultIcon, tint } = VARIANTS[variant];
  const Icon = icon ?? DefaultIcon;

  return (
    <div
      role={variant === "error" ? "alert" : "status"}
      className={cn(
        "bg-card border-border shadow-surface flex w-full flex-col items-center justify-center gap-3 rounded-lg border px-6 py-10 text-center",
        className
      )}
    >
      <Icon className={cn("size-5 shrink-0", tint)} aria-hidden />
      <div className="flex max-w-md flex-col items-center gap-1">
        <p className="text-foreground text-sm font-semibold">{title}</p>
        {description ? <p className="text-text-secondary text-sm leading-6">{description}</p> : null}
      </div>
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  );
}
