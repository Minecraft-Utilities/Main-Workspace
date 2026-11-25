import { cn } from "@/common/utils";
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { Inbox } from "lucide-react";
import type { ReactNode } from "react";

export interface EmptyStateProps {
  title: string;
  description?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({ title, description, icon, action, className }: EmptyStateProps) {
  return (
    <Empty
      className={cn("border-border bg-card shadow-surface max-w-md gap-3 rounded-lg border p-6", className)}
    >
      <EmptyMedia variant="icon">{icon ?? <Inbox className="size-5" />}</EmptyMedia>
      <EmptyHeader>
        <EmptyTitle>{title}</EmptyTitle>
        {description ? <EmptyDescription>{description}</EmptyDescription> : null}
      </EmptyHeader>
      {action ? <div className="mt-1">{action}</div> : null}
    </Empty>
  );
}
