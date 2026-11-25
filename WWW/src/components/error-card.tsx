import { errorMessage, isApiErrorMessage } from "@/common/error-message";
import { cn } from "@/common/utils";
import Card from "@/components/ui/card";
import { CircleAlert } from "lucide-react";
import type { ErrorResponse } from "mcutils-js-api";
import type { ReactNode } from "react";

export interface ErrorCardProps {
  title: string;
  message?: ReactNode;
  /**
   * Structured API error. When set it takes precedence over message and is mapped to human copy,
   * so backend text never reaches the page.
   */
  error?: ErrorResponse | Error | number | string;
  action?: ReactNode;
  className?: string;
}

export function ErrorCard({ title, message, error, action, className }: ErrorCardProps) {
  const body =
    error !== undefined
      ? errorMessage(error)
      : typeof message === "string" && isApiErrorMessage(message)
        ? errorMessage(message)
        : message;

  return (
    <Card className={cn("w-full max-w-xl items-center gap-3 p-6 text-center", className)}>
      <CircleAlert className="text-danger size-5 shrink-0" aria-hidden />
      <p className="text-foreground text-sm font-semibold">{title}</p>
      {body ? <div className="text-text-secondary text-sm">{body}</div> : null}
      {action ? <div className="mt-1">{action}</div> : null}
    </Card>
  );
}
