import { type ClassValue } from "clsx";

import { cn } from "@/common/utils";

export interface CardProps {
  children: React.ReactNode;
  className?: ClassValue;
}

export interface CardHeaderProps {
  children: React.ReactNode;
  className?: ClassValue;
  variant?: "default" | "destructive";
}

export function CardHeader({ children, className, variant = "default" }: CardHeaderProps) {
  return (
    <div
      className={cn(
        "border-border text-foreground flex h-11 shrink-0 items-center gap-2 border-b px-4 text-sm font-semibold",
        variant === "destructive" && "border-danger-border bg-danger-surface",
        className
      )}
    >
      {children}
    </div>
  );
}

export interface CardContentProps {
  children: React.ReactNode;
  className?: ClassValue;
}

export function CardContent({ children, className }: CardContentProps) {
  return <div className={cn("p-4", className)}>{children}</div>;
}

export interface CardFooterProps {
  children: React.ReactNode;
  className?: ClassValue;
  variant?: "default" | "destructive";
}

export function CardFooter({ children, className, variant = "default" }: CardFooterProps) {
  return (
    <div
      className={cn(
        "border-border mt-auto w-full border-t px-4 py-3 text-sm",
        variant === "destructive" && "border-danger-border bg-danger-surface",
        className
      )}
    >
      {children}
    </div>
  );
}

export default function Card({ children, className }: CardProps) {
  return (
    <div className={cn("bg-card border-border shadow-surface flex flex-col rounded-lg border", className)}>
      {children}
    </div>
  );
}
