"use client";

import { cn, truncateString } from "@/common/utils";
import { Button } from "@/components/ui/button";
import { CheckIcon, CopyIcon } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { toast } from "sonner";

export type CopyButtonVariant = "icon" | "text" | "inline";

export interface CopyButtonProps {
  value: string;
  label: string;
  variant?: CopyButtonVariant;
  children?: ReactNode;
  className?: string;
}

export function CopyButton({ value, label, variant = "icon", children, className }: CopyButtonProps) {
  const [copied, setCopied] = useState(false);
  const resetTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (resetTimer.current) {
        clearTimeout(resetTimer.current);
      }
    };
  }, []);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      if (resetTimer.current) {
        clearTimeout(resetTimer.current);
      }
      resetTimer.current = setTimeout(() => setCopied(false), 1200);
      toast.success("Copied to your clipboard", {
        description: <p className="text-xs break-all whitespace-pre-wrap">{truncateString(value, 100)}</p>,
      });
    } catch {
      setCopied(false);
      toast.error("Failed to copy");
    }
  }

  if (variant === "icon") {
    return (
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label={label}
        onClick={handleCopy}
        className={className}
      >
        {copied ? <CheckIcon className="size-4" /> : <CopyIcon className="size-4" />}
      </Button>
    );
  }

  if (variant === "text") {
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        aria-label={label}
        onClick={handleCopy}
        className={className}
      >
        {copied ? <CheckIcon className="size-4" /> : <CopyIcon className="size-4" />}
        <span>{children ?? label}</span>
      </Button>
    );
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label={label}
      className={cn(
        "text-muted-foreground hover:bg-control-hover hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring inline-flex shrink-0 items-center gap-1 rounded-sm px-1.5 py-0.5 text-xs font-medium transition-colors duration-[120ms]",
        className
      )}
    >
      {copied ? <CheckIcon className="size-3.5" /> : <CopyIcon className="size-3.5" />}
      <span>{children ?? label}</span>
    </button>
  );
}
