import { cn } from "@/common/utils";
import type { ReactNode } from "react";

export interface PageShellProps {
  children: ReactNode;
  className?: string;
}

export function PageShell({ children, className }: PageShellProps) {
  return (
    <div
      className={cn(
        "mx-auto mt-10 flex w-full max-w-[980px] flex-col items-center justify-center gap-10",
        className
      )}
    >
      {children}
    </div>
  );
}

export interface PageTitleProps {
  children: ReactNode;
  description?: ReactNode;
  className?: string;
}

export function PageTitle({ children, description, className }: PageTitleProps) {
  return (
    <header className={cn("w-full text-left", className)}>
      <h1 className="text-foreground text-3xl font-bold tracking-tight sm:text-4xl">{children}</h1>
      {description ? <p className="text-muted-foreground mt-2 text-sm">{description}</p> : null}
    </header>
  );
}
