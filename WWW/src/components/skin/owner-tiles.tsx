"use client";

import { env } from "@/common/env";
import { formatNumberWithCommas } from "@/common/utils";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import Pagination from "@/components/ui/pagination";
import { StateBlock } from "@/components/ui/state-block";
import { Search } from "lucide-react";
import Link from "next/link";
import { useMemo, useState, type MouseEvent } from "react";

export type OwnerTilesKind = "cape" | "skin";

const PAGE_SIZE = 20;

export interface OwnerTilesProps {
  accounts: string[];
  currentOwners: number;
  kind: OwnerTilesKind;
}

export default function OwnerTiles({ accounts, currentOwners, kind }: OwnerTilesProps) {
  const [filter, setFilter] = useState("");
  const [page, setPage] = useState(1);

  const filteredAccounts = useMemo(() => {
    const query = filter.trim().toLowerCase();
    if (!query) {
      return accounts;
    }
    return accounts.filter(account => account.toLowerCase().includes(query));
  }, [accounts, filter]);

  const totalPages = Math.max(1, Math.ceil(filteredAccounts.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const firstIndex = (currentPage - 1) * PAGE_SIZE;
  const visibleAccounts = filteredAccounts.slice(firstIndex, firstIndex + PAGE_SIZE);
  const query = filter.trim();

  function handlePageClick(event: MouseEvent<HTMLDivElement>) {
    const anchor = (event.target as HTMLElement).closest("a");
    if (!anchor) {
      return;
    }

    const match = anchor.getAttribute("href")?.match(/^#owners-page-(\d+)$/);
    if (!match) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    setPage(Number(match[1]));
  }

  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader className="justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <span>Owners</span>
          <span className="text-muted-foreground truncate text-xs font-medium tabular-nums">
            {formatNumberWithCommas(currentOwners)}
          </span>
        </div>
        <InputGroup className="h-8 w-36 shrink-0 sm:w-44">
          <InputGroupAddon align="inline-start">
            <Search aria-hidden />
          </InputGroupAddon>
          <InputGroupInput
            type="search"
            value={filter}
            onChange={event => {
              setFilter(event.target.value);
              setPage(1);
            }}
            placeholder="Filter"
            aria-label="Filter owners"
            className="text-base sm:text-sm"
          />
        </InputGroup>
      </CardHeader>

      <CardContent className="p-0">
        {visibleAccounts.length === 0 ? (
          <div className="p-4">
            <StateBlock
              variant="empty"
              title={accounts.length === 0 ? "No owners recorded yet" : `No owners match "${query}"`}
              description={
                accounts.length === 0
                  ? kind === "cape"
                    ? "Nobody has been seen wearing this cape yet."
                    : "Nobody has been seen using this skin yet."
                  : "Try a different name."
              }
              className="border-0 bg-transparent px-0 shadow-none"
            />
          </div>
        ) : (
          <ul className="-mb-px grid md:grid-cols-2">
            {visibleAccounts.map(account => (
              <li key={account} className="border-border/60 border-b md:odd:border-r">
                <Link
                  href={`/player/${encodeURIComponent(account)}`}
                  className="group hover:bg-control-hover focus-visible:outline-ring flex min-w-0 items-center gap-3 px-4 py-2 transition-colors duration-[120ms] ease-out focus-visible:outline-2 focus-visible:outline-offset-[-2px]"
                >
                  <img
                    src={`${env.NEXT_PUBLIC_API_URL}/skins/${encodeURIComponent(account)}/face.png?size=64`}
                    alt=""
                    onError={event => {
                      event.currentTarget.style.visibility = "hidden";
                    }}
                    width={32}
                    height={32}
                    loading="lazy"
                    className="pixelated bg-stage size-8 shrink-0 rounded-sm object-contain"
                  />
                  <span className="text-foreground group-hover:text-link min-w-0 flex-1 truncate text-sm font-medium">
                    {account}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {totalPages > 1 && (
          <div className="border-border border-t px-4 py-3" onClickCapture={handlePageClick}>
            <Pagination
              page={currentPage}
              totalItems={filteredAccounts.length}
              itemsPerPage={PAGE_SIZE}
              hrefForPage={target => `#owners-page-${target}`}
            />
          </div>
        )}
      </CardContent>
    </Card>
  );
}
