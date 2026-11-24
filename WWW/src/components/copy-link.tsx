"use client";

import { cn, truncateString } from "@/common/utils";
import { toast } from "sonner";

export interface CopyLinkProps {
  text: string;
  className?: string;
}

export default function CopyLink({ text, className }: CopyLinkProps) {
  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(text);
      toast.success("Copied to your clipboard", {
        description: <p className="text-xs break-all whitespace-pre-wrap">{truncateString(text, 100)}</p>,
      });
    } catch {
      toast.error("Failed to copy");
    }
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      className={cn("shrink-0 text-sm text-[#7289da] transition-colors hover:text-[#8ea1e8]", className)}
    >
      Copy
    </button>
  );
}
