"use client";

import { useRef } from "react";
import QuerySearch, { type QuerySearchHandle } from "../lookup/query-search";

const examples = [
  { label: "Notch", query: "Notch" },
  { label: "hypixel.net", query: "hypixel.net" },
];

export default function LandingQuerySearch() {
  const searchRef = useRef<QuerySearchHandle>(null);

  return (
    <div className="flex w-full max-w-xl flex-col items-center gap-3">
      <QuerySearch
        ref={searchRef}
        showSubmit
        placeholder="Search a player, UUID, or server"
        className="h-12"
      />
      <div className="flex flex-wrap items-center justify-center gap-2">
        <span className="text-muted-foreground text-[13px] font-medium">Try</span>
        {examples.map(example => (
          <button
            key={example.label}
            type="button"
            onClick={() => searchRef.current?.submit(example.query)}
            className="border-border bg-control text-muted-foreground hover:bg-control-hover hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring h-8 rounded-sm border px-2.5 text-[13px] font-medium transition-colors duration-[120ms] ease-out"
          >
            {example.label}
          </button>
        ))}
      </div>
    </div>
  );
}
