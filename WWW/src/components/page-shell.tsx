import { cn } from "@/common/utils";
import { ArrowLeftIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

export interface PageShellProps {
  children: ReactNode;
  className?: string;
}

export function PageShell({ children, className }: PageShellProps) {
  return (
    <div
      className={cn(
        "mx-auto mt-10 flex w-full max-w-[1200px] flex-col items-center justify-center gap-10 px-4 md:px-6",
        className
      )}
    >
      {children}
    </div>
  );
}

export interface PageHeaderProps {
  children: ReactNode;
  description?: ReactNode;
  backLink?: { href: string; label: string };
  actions?: ReactNode;
  className?: string;
}

export function PageHeader({ children, description, backLink, actions, className }: PageHeaderProps) {
  return (
    <header
      className={cn(
        "flex w-full flex-col items-center gap-3 text-center sm:items-stretch sm:text-left",
        className
      )}
    >
      {backLink ? (
        <Link
          href={backLink.href}
          className="text-muted-foreground hover:text-foreground focus-visible:outline-ring inline-flex w-fit items-center gap-1 self-start rounded-sm text-[13px] font-medium transition-colors duration-[120ms] focus-visible:outline-2 focus-visible:outline-offset-2"
        >
          <ArrowLeftIcon className="size-4" aria-hidden />
          {backLink.label}
        </Link>
      ) : null}
      <div className="flex w-full flex-col items-center gap-3 sm:flex-row sm:items-end sm:justify-between sm:gap-4">
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="text-foreground text-2xl leading-8 font-[650] tracking-[-0.02em] sm:text-[28px] sm:leading-9">
            {children}
          </h1>
          {description ? (
            <p className="text-muted-foreground text-[13px] leading-[18px] font-medium">{description}</p>
          ) : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  );
}

export interface PageTitleProps {
  children: ReactNode;
  description?: ReactNode;
  className?: string;
}

export function PageTitle({ children, description, className }: PageTitleProps) {
  return (
    <header className={cn("w-full text-center sm:text-left", className)}>
      <h1 className="text-foreground text-2xl leading-8 font-[650] tracking-[-0.02em] sm:text-[28px] sm:leading-9">
        {children}
      </h1>
      {description ? (
        <p className="text-muted-foreground mt-1 text-[13px] leading-[18px] font-medium">{description}</p>
      ) : null}
    </header>
  );
}
