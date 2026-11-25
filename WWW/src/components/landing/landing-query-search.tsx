"use client";

import Card, { CardContent, CardFooter } from "@/components/ui/card";
import type { ErrorResponse } from "mcutils-js-api";
import { useRef, useState } from "react";
import QuerySearch, { type QuerySearchHandle } from "../lookup/query-search";

const examples = [
  { label: "Notch", query: "Notch" },
  { label: "hypixel.net", query: "hypixel.net" },
];

export default function LandingQuerySearch() {
  const [queryError, setQueryError] = useState<ErrorResponse | undefined>(undefined);
  const searchRef = useRef<QuerySearchHandle>(null);

  return (
    <div className="flex w-full max-w-xl flex-col">
      <Card className="w-full">
        <CardContent>
          <QuerySearch
            ref={searchRef}
            landingPage
            placeholder="Search a player, UUID, or server"
            setQueryError={setQueryError}
          />
        </CardContent>
        <CardFooter className="text-xs">
          {queryError ? (
            <p className="text-destructive">{queryError.message}</p>
          ) : (
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-muted-foreground">Try:</span>
              {examples.map(example => (
                <button
                  key={example.label}
                  type="button"
                  onClick={() => searchRef.current?.submit(example.query)}
                  className="border-border/60 bg-muted/30 text-muted-foreground hover:border-border hover:bg-accent hover:text-foreground rounded-full border px-2 py-0.5 transition-colors"
                >
                  {example.label}
                </button>
              ))}
            </div>
          )}
        </CardFooter>
      </Card>
    </div>
  );
}
