import { cn } from "@/common/utils";
import { ArrowLeftIcon } from "lucide-react";
import Link from "next/link";

export interface BackLinkProps {
  href: string;
  label: string;
  className?: string;
}

export function BackLink({ href, label, className }: BackLinkProps) {
  return (
    <Link
      href={href}
      className={cn(
        "text-muted-foreground hover:text-foreground focus-visible:ring-ring inline-flex w-full items-center gap-1 text-sm transition-colors focus-visible:ring-2 focus-visible:outline-none",
        className
      )}
    >
      <ArrowLeftIcon className="size-4" aria-hidden />
      {label}
    </Link>
  );
}
