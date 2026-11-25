import { cn } from "@/common/utils";
import type { ReactNode } from "react";

export const MEDIA_GRID = "grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5";

export interface MediaGridProps {
  children: ReactNode;
  className?: string;
}

export function MediaGrid({ children, className }: MediaGridProps) {
  return <div className={cn(MEDIA_GRID, className)}>{children}</div>;
}
