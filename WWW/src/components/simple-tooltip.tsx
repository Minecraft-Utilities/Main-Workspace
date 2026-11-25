"use client";

import { cn } from "@/common/utils";
import { Tooltip as TooltipPrimitive } from "radix-ui";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useIsMobile } from "./context/viewport-context";

const triggerClassName = "flex w-full items-center justify-center";

export interface SimpleTooltipProps {
  children: React.ReactNode;
  display: React.ReactNode | string;
  side?: "top" | "right" | "bottom" | "left";
  className?: string;
  showOnMobile?: boolean;
  closeDelayDuration?: number;
}

export default function SimpleTooltip({
  children,
  display,
  side = "top",
  className,
  showOnMobile = false,
  closeDelayDuration = 0,
}: SimpleTooltipProps) {
  const isMobile = useIsMobile();
  const [open, setOpen] = useState(false);
  const closeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearCloseTimeout = useCallback(() => {
    if (closeTimeoutRef.current !== null) {
      clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
  }, []);

  useEffect(() => clearCloseTimeout, [clearCloseTimeout]);

  const handleOpenChange = useCallback(
    (next: boolean) => {
      clearCloseTimeout();
      if (next) {
        setOpen(true);
        return;
      }
      if (closeDelayDuration > 0) {
        closeTimeoutRef.current = setTimeout(() => {
          setOpen(false);
          closeTimeoutRef.current = null;
        }, closeDelayDuration);
        return;
      }
      setOpen(false);
    },
    [clearCloseTimeout, closeDelayDuration]
  );

  if (isMobile && !showOnMobile) {
    return (
      <div>
        <div className={cn(triggerClassName, className)}>{children}</div>
      </div>
    );
  }

  return (
    <TooltipPrimitive.Provider delayDuration={0} skipDelayDuration={0}>
      <TooltipPrimitive.Root open={open} onOpenChange={handleOpenChange} delayDuration={0}>
        {/* The outer div is the flex item, so the full-width inner wrapper sizes to it instead of to the row. */}
        <TooltipPrimitive.Trigger asChild>
          <div>
            <div className={cn(triggerClassName, className)}>{children}</div>
          </div>
        </TooltipPrimitive.Trigger>
        <TooltipPrimitive.Portal>
          <TooltipPrimitive.Content
            side={side}
            sideOffset={6}
            className="bg-popover text-popover-foreground border-border-strong shadow-overlay z-50 max-w-[400px] rounded-sm border px-2.5 py-1.5 text-[13px]"
          >
            {typeof display === "string" ? <p className="text-wrap">{display}</p> : display}
          </TooltipPrimitive.Content>
        </TooltipPrimitive.Portal>
      </TooltipPrimitive.Root>
    </TooltipPrimitive.Provider>
  );
}
