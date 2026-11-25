"use client";

import { env } from "@/common/env";
import { formatNumberWithCommas } from "@/common/utils";
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import PlayerPreview from "../player/player-preview";
import SimpleLink from "../simple-link";

export type OwnerTilesKind = "cape" | "skin";

interface OwnerTilesProps {
  accounts: string[];
  remaining: number;
  kind: OwnerTilesKind;
}

export default function OwnerTiles({ accounts, remaining, kind }: OwnerTilesProps) {
  const [filter, setFilter] = useState("");
  const noun = kind === "cape" ? "wearers" : "users";
  const singular = kind === "cape" ? "wearer" : "user";

  const visibleAccounts = useMemo(() => {
    const query = filter.trim().toLowerCase();
    if (!query) {
      return accounts;
    }
    return accounts.filter(account => account.toLowerCase().includes(query));
  }, [accounts, filter]);

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2">
      <InputGroup className="h-8 shrink-0">
        <InputGroupAddon align="inline-start">
          <Search aria-hidden />
        </InputGroupAddon>
        <InputGroupInput
          type="search"
          value={filter}
          onChange={event => setFilter(event.target.value)}
          placeholder={`Filter ${noun}...`}
          aria-label={`Filter ${noun}`}
          className="text-xs"
        />
      </InputGroup>

      {visibleAccounts.length === 0 ? (
        <p className="text-muted-foreground px-1 py-2 text-xs">
          {accounts.length === 0 ? `No ${noun} recorded yet.` : `No matching ${singular}s.`}
        </p>
      ) : (
        <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-2 content-start gap-1.5 overflow-y-auto pr-1 sm:grid-cols-3">
          {visibleAccounts.map(account => (
            <PlayerPreview
              key={account}
              nameOrUuid={account}
              trigger={
                <SimpleLink
                  href={`/player/${account}`}
                  className="border-border/60 bg-muted/30 hover:border-border hover:bg-accent flex min-w-0 items-center gap-2 rounded-md border px-2 py-1.5"
                >
                  <img
                    src={`${env.NEXT_PUBLIC_API_URL}/skins/${encodeURIComponent(account)}/face.png?size=64`}
                    alt=""
                    width={24}
                    height={24}
                    loading="lazy"
                    className="size-6 shrink-0 rounded-sm object-cover"
                  />
                  <span className="truncate text-xs">{account}</span>
                </SimpleLink>
              }
            />
          ))}
        </div>
      )}

      {remaining > 0 && (
        <p className="text-muted-foreground shrink-0 text-xs">
          + {formatNumberWithCommas(remaining)} more...
        </p>
      )}
    </div>
  );
}
