import { cn } from "@/common/utils";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import type { ReactNode } from "react";

export interface ErrorCardProps {
  title: string;
  message: ReactNode;
  className?: string;
}

export function ErrorCard({ title, message, className }: ErrorCardProps) {
  return (
    <Card
      className={cn("border-destructive/50 bg-destructive/10 w-full max-w-xl overflow-hidden p-0", className)}
    >
      <CardHeader variant="destructive" className="text-red-300">
        {title}
      </CardHeader>
      <CardContent>
        <p className="text-muted-foreground text-sm">{message}</p>
      </CardContent>
    </Card>
  );
}
