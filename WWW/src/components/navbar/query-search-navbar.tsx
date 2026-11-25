"use client";

import { cn } from "@/common/utils";
import { SearchIcon, XIcon } from "lucide-react";
import { usePathname } from "next/navigation";
import { useCallback, useRef, useState } from "react";
import QuerySearch from "../lookup/query-search";
import { Button } from "../ui/button";

export default function QuerySearchNavbar() {
  const pathname = usePathname();
  // The path the search was opened on; navigating away collapses it without an effect.
  const [openedAtPath, setOpenedAtPath] = useState<string | null>(null);
  const openButtonRef = useRef<HTMLButtonElement>(null);
  const mobileOpen = openedAtPath !== null && openedAtPath === pathname;

  const closeSearch = useCallback(() => {
    setOpenedAtPath(null);
    openButtonRef.current?.focus();
  }, []);

  return (
    <div className="flex min-w-0 items-center justify-end md:relative">
      <Button
        ref={openButtonRef}
        type="button"
        variant="ghost"
        size="icon-sm"
        className="shrink-0 md:hidden"
        aria-label="Open search"
        aria-expanded={mobileOpen}
        onClick={() => setOpenedAtPath(pathname)}
      >
        <SearchIcon className="size-5" aria-hidden />
      </Button>

      <div
        className={cn(
          "min-w-0",
          mobileOpen
            ? "bg-background absolute inset-y-0 right-2 left-2 z-50 flex items-center gap-1 md:static md:bg-transparent"
            : "hidden md:block"
        )}
        onKeyDown={event => {
          if (mobileOpen && event.key === "Escape") {
            event.stopPropagation();
            closeSearch();
          }
        }}
      >
        <div
          className={cn(
            "min-w-0",
            mobileOpen ? "flex-1 md:w-72 md:flex-none lg:w-96" : "w-40 md:w-72 lg:w-96"
          )}
        >
          <QuerySearch autoFocus={mobileOpen} className="w-full" />
        </div>
        <Button type="button" variant="ghost" size="sm" className="shrink-0 md:hidden" onClick={closeSearch}>
          <XIcon className="size-4" aria-hidden />
          <span className="text-xs">Cancel</span>
        </Button>
      </div>
    </div>
  );
}
