"use client";

import { cn } from "@/common/utils";
import { SearchIcon, XIcon } from "lucide-react";
import type { ErrorResponse } from "mcutils-js-api";
import { usePathname } from "next/navigation";
import { useState } from "react";
import QuerySearch from "../lookup/query-search";
import { Button } from "../ui/button";
import Card from "../ui/card";

export default function QuerySearchNavbar() {
  const pathname = usePathname();
  const [queryError, setQueryError] = useState<ErrorResponse | undefined>(undefined);
  // The path the search was opened on; navigating away collapses it without an effect.
  const [openedAtPath, setOpenedAtPath] = useState<string | null>(null);
  const mobileOpen = openedAtPath !== null && openedAtPath === pathname;

  return (
    <div className="flex min-w-0 items-center justify-end md:relative">
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        className="shrink-0 md:hidden"
        aria-label="Open search"
        aria-expanded={mobileOpen}
        onClick={() => setOpenedAtPath(pathname)}
      >
        <SearchIcon className="size-5" />
      </Button>

      <div
        className={cn(
          "min-w-0",
          mobileOpen
            ? "bg-background absolute inset-y-0 right-2 left-2 z-50 flex items-center gap-1 md:static md:bg-transparent"
            : "hidden md:block"
        )}
      >
        <div className={cn("min-w-0", mobileOpen ? "flex-1 md:w-56 md:flex-none" : "w-40 md:w-56")}>
          <QuerySearch
            setQueryError={setQueryError}
            autoFocus={mobileOpen}
            placeholder={mobileOpen ? "Search a player, UUID, or server" : undefined}
            className="w-full"
          />
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="shrink-0 md:hidden"
          aria-label="Close search"
          onClick={() => setOpenedAtPath(null)}
        >
          <XIcon className="size-5" />
        </Button>
      </div>

      {queryError && (
        <Card className="bg-card absolute top-full right-0 z-50 mt-1 w-full max-w-[min(100vw-2rem,24rem)] min-w-48 px-3 py-2 shadow-md">
          <p className="text-destructive text-xs leading-snug" role="alert">
            {queryError.message}
          </p>
        </Card>
      )}
    </div>
  );
}
