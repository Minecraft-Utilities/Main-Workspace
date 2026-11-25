"use client";

import { cn } from "@/common/utils";
import { CheckIcon, CopyIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";

export interface IdChipProps {
  /** What the value is, used in the accessible name and the toast ("Copy UUID"). */
  label: string;
  value: string;
  className?: string;
}

export function IdChip({ label, value, className }: IdChipProps) {
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
      toast.success(`Copied ${label}`);
    } catch {
      setCopied(false);
      toast.error("Failed to copy");
    }
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label={`Copy ${label}`}
      className={cn(
        "bg-control border-border text-foreground hover:bg-control-hover focus-visible:outline-ring inline-flex h-10 max-w-full md:h-7 items-center gap-1.5 rounded-sm border px-2 font-mono text-xs transition-colors duration-[120ms] ease-out focus-visible:outline-2 focus-visible:outline-offset-2",
        className
      )}
    >
      <span className="min-w-0 truncate">{value}</span>
      {copied ? (
        <CheckIcon className="text-success size-3.5 shrink-0" aria-hidden />
      ) : (
        <CopyIcon className="text-muted-foreground size-3.5 shrink-0" aria-hidden />
      )}
    </button>
  );
}
