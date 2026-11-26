"use client";

import { bedrockPath, GAMERTAG_PATTERN, isBedrockQuery } from "@/common/bedrock";
import { errorMessage } from "@/common/error-message";
import { mcUtilsApi } from "@/common/mc-utils";
import { cn, isIpOrDomain } from "@/common/utils";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
import { zodResolver } from "@hookform/resolvers/zod";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useDebounce } from "@uidotdev/usehooks";
import { Clock, Loader2, Search, Server, User, X } from "lucide-react";
import {
  imageUrl,
  type BasicPlayer,
  type ErrorResponse,
  type Platform,
  type ServerRegistryEntry,
} from "mcutils-js-api";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useId, useImperativeHandle, useRef, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { Button } from "../ui/button";
import { lookupRowClassName } from "./lookup-row";
import ServerEditionDialog from "./server-edition-dialog";

export const QUERY_SEARCH_PLACEHOLDER = "Search a player, gamertag, or server";
export const QUERY_SEARCH_VALIDATION_MESSAGE =
  "That's not a valid username, gamertag, UUID or server address";

const HISTORY_MAX = 10;

const PLAYER_NAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/;
const UUID_PATTERN = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;

function isLookupQuery(value: string): boolean {
  return (
    PLAYER_NAME_PATTERN.test(value) ||
    UUID_PATTERN.test(value) ||
    GAMERTAG_PATTERN.test(value) ||
    isIpOrDomain(value)
  );
}

function useLocalStorage<T>(key: string, initialValue: T): [T, (value: T | ((prev: T) => T)) => void] {
  const [storedValue, setStoredValue] = useState<T>(() => {
    if (typeof window === "undefined") return initialValue;
    try {
      const item = window.localStorage.getItem(key);
      return item !== null ? (JSON.parse(item) as T) : initialValue;
    } catch {
      return initialValue;
    }
  });

  const setValue = useCallback(
    (value: T | ((prev: T) => T)) => {
      setStoredValue(prev => {
        const next = typeof value === "function" ? (value as (prev: T) => T)(prev) : value;
        try {
          window.localStorage.setItem(key, JSON.stringify(next));
        } catch {}
        return next;
      });
    },
    [key]
  );

  useEffect(() => {
    const handler = (e: StorageEvent) => {
      if (e.key !== key) return;
      try {
        setStoredValue(e.newValue !== null ? (JSON.parse(e.newValue) as T) : initialValue);
      } catch {}
    };
    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
  }, [key, initialValue]);

  return [storedValue, setValue];
}

type HistoryEntry = { query: string; path: string };

type SearchOption =
  | { kind: "recent"; key: string; entry: HistoryEntry }
  | { kind: "player"; key: string; entry: BasicPlayer }
  | { kind: "server"; key: string; entry: ServerRegistryEntry };

const querySearchSchema = z.object({
  query: z
    .string()
    .transform(s => s.trim())
    .pipe(z.string().refine(isLookupQuery, QUERY_SEARCH_VALIDATION_MESSAGE)),
});

type QuerySearchValues = z.infer<typeof querySearchSchema>;

export interface QuerySearchHandle {
  submit: (query: string) => void;
}

export interface QuerySearchProps {
  showSubmit?: boolean;
  className?: string;
  placeholder?: string;
  autoFocus?: boolean;
  /**
   * Notification hook for callers that mirror the lookup error elsewhere.
   * The error is always rendered inline under the input, so do not render it twice.
   */
  setQueryError?: (invalid: ErrorResponse | undefined) => void;
  ref?: React.Ref<QuerySearchHandle>;
}

export default function QuerySearch({
  showSubmit = false,
  className,
  placeholder = QUERY_SEARCH_PLACEHOLDER,
  autoFocus,
  setQueryError,
  ref,
}: QuerySearchProps) {
  const router = useRouter();
  const [serverDialogOpen, setServerDialogOpen] = useState(false);
  const [pendingServer, setPendingServer] = useState("");
  const [isLookupLoading, setIsLookupLoading] = useState(false);
  const [lookupError, setLookupError] = useState<ErrorResponse | undefined>(undefined);
  const [serverPopoverOpen, setServerPopoverOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [history, setHistory] = useLocalStorage<HistoryEntry[]>("search-history", []);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const errorId = useId();
  const listboxId = useId();

  const addEntry = useCallback(
    (entry: HistoryEntry) => {
      setHistory(prev => {
        const filtered = (prev ?? []).filter(h => h.path !== entry.path);
        return [entry, ...filtered].slice(0, HISTORY_MAX);
      });
    },
    [setHistory]
  );

  const form = useForm<QuerySearchValues>({
    resolver: zodResolver(querySearchSchema),
    defaultValues: { query: "" },
  });

  const queryValue = useWatch({ control: form.control, name: "query" });
  const debouncedQuery = useDebounce(queryValue.trim(), 300);

  const {
    data: serverEntries,
    isFetching: isServerSearchFetching,
    isSuccess: isServerSearchSuccess,
  } = useQuery({
    queryKey: ["serverRegistry", debouncedQuery],
    queryFn: async (): Promise<ServerRegistryEntry[]> => {
      const result = await mcUtilsApi.server().search(debouncedQuery);
      if (result.error) throw new Error(errorMessage(result.error));
      return result.data;
    },
    placeholderData: keepPreviousData,
    enabled: !!debouncedQuery,
  });

  const {
    data: playerEntry,
    isFetching: isPlayerSearchFetching,
    isSuccess: isPlayerSearchSuccess,
  } = useQuery({
    queryKey: ["playerSearch", debouncedQuery],
    queryFn: async (): Promise<BasicPlayer[] | null> => {
      const result = await mcUtilsApi.player().search(debouncedQuery);
      if (result.error) return null;
      return result.data;
    },
    placeholderData: keepPreviousData,
    enabled: !!debouncedQuery,
  });

  const hasServerResults = !!serverEntries && serverEntries.length > 0;
  const hasPlayerResults = Array.isArray(playerEntry) && playerEntry.length > 0;
  const isSearching = isServerSearchFetching || isPlayerSearchFetching;
  const searchSettled = isServerSearchSuccess && isPlayerSearchSuccess;
  const showHistoryDropdown = !debouncedQuery && queryValue.trim().length === 0 && history.length > 0;

  const recentOptions: SearchOption[] = showHistoryDropdown
    ? history.map(entry => ({ kind: "recent" as const, key: `recent-${entry.path}`, entry }))
    : [];
  const playerOptions: SearchOption[] = showHistoryDropdown
    ? []
    : (playerEntry ?? []).map(entry => ({
        kind: "player" as const,
        key: `player-${entry.uniqueId}`,
        entry,
      }));
  const serverOptions: SearchOption[] = showHistoryDropdown
    ? []
    : (serverEntries ?? []).map(entry => ({
        kind: "server" as const,
        key: `server-${entry.serverId}`,
        entry,
      }));
  const options = [...recentOptions, ...playerOptions, ...serverOptions];
  const highlightedIndex = activeIndex >= 0 && activeIndex < options.length ? activeIndex : -1;

  const listOpen =
    serverPopoverOpen &&
    (showHistoryDropdown || isSearching || (searchSettled && (hasPlayerResults || hasServerResults)));

  const optionId = (index: number) => `${listboxId}-option-${index}`;

  useEffect(() => {
    if (highlightedIndex < 0) {
      return;
    }
    document.getElementById(optionId(highlightedIndex))?.scrollIntoView({ block: "nearest" });
  }, [highlightedIndex, listboxId]);

  useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      const target = event.target as HTMLElement | null;
      const tagName = target?.tagName;
      if (
        tagName === "INPUT" ||
        tagName === "TEXTAREA" ||
        tagName === "SELECT" ||
        target?.isContentEditable
      ) {
        return;
      }
      event.preventDefault();
      inputRef.current?.focus();
    };
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, []);

  useEffect(() => {
    if (autoFocus) {
      inputRef.current?.focus();
    }
  }, [autoFocus]);

  const clearError = useCallback(() => {
    setLookupError(undefined);
    setQueryError?.(undefined);
  }, [setQueryError]);

  const onSubmit = useCallback(
    async (data: QuerySearchValues) => {
      const trimmed = data.query.trim();
      if (!trimmed) {
        return;
      }

      if (isIpOrDomain(trimmed)) {
        setPendingServer(trimmed);
        setServerDialogOpen(true);
        setServerPopoverOpen(false);
        return;
      }

      setLookupError(undefined);
      setQueryError?.(undefined);
      setIsLookupLoading(true);

      const submitBedrock = async (query: string) => {
        const bedrock = await mcUtilsApi.bedrock().player(query);
        if (bedrock.error) {
          return bedrock.error;
        }
        const path = bedrockPath(bedrock.data.username);
        addEntry({ query: bedrock.data.username, path });
        router.push(path);
        form.reset();
        setServerPopoverOpen(false);
        return undefined;
      };

      try {
        if (isBedrockQuery(trimmed)) {
          const bedrockError = await submitBedrock(trimmed);
          if (bedrockError) {
            setLookupError(bedrockError);
            setQueryError?.(bedrockError);
          }
          return;
        }

        const result = await mcUtilsApi.player().fetch(trimmed);
        if (result.error) {
          setLookupError(result.error);
          setQueryError?.(result.error);
          return;
        }
        const path = `/player/${encodeURIComponent(trimmed)}`;
        addEntry({ query: trimmed, path });
        router.push(path);
        form.reset();
        setServerPopoverOpen(false);
      } finally {
        setIsLookupLoading(false);
      }
    },
    [router, form, setQueryError, addEntry]
  );

  const handleServerEdition = useCallback(
    (edition: Platform) => {
      if (!pendingServer) return;
      const path = `/server/${edition.toLowerCase()}/${encodeURIComponent(pendingServer)}`;
      addEntry({ query: pendingServer, path });
      router.push(path);
      form.reset();
      setPendingServer("");
      setServerDialogOpen(false);
    },
    [pendingServer, router, form, addEntry]
  );

  const handlePlayerEntryClick = useCallback(
    (entry: BasicPlayer) => {
      const path = `/player/${encodeURIComponent(entry.username)}`;
      addEntry({ query: entry.username, path });
      router.push(path);
      form.reset();
      setServerPopoverOpen(false);
    },
    [router, form, addEntry]
  );

  const handleServerEntryClick = useCallback(
    (entry: ServerRegistryEntry) => {
      const hostname = entry.hostnames[0];
      if (!hostname) return;
      const path = `/server/${entry.platform.toLowerCase()}/${encodeURIComponent(hostname)}`;
      addEntry({ query: hostname, path });
      router.push(path);
      form.reset();
      setServerPopoverOpen(false);
    },
    [router, form, addEntry]
  );

  const selectOption = useCallback(
    (option: SearchOption) => {
      if (option.kind === "recent") {
        addEntry(option.entry);
        router.push(option.entry.path);
        form.reset();
        setServerPopoverOpen(false);
        return;
      }
      if (option.kind === "player") {
        handlePlayerEntryClick(option.entry);
        return;
      }
      handleServerEntryClick(option.entry);
    },
    [addEntry, router, form, handlePlayerEntryClick, handleServerEntryClick]
  );

  const submitQuery = useCallback(
    (query: string) => {
      setActiveIndex(-1);
      form.setValue("query", query, { shouldValidate: true });
      void form.handleSubmit(onSubmit)();
    },
    [form, onSubmit]
  );

  useImperativeHandle(ref, () => ({ submit: submitQuery }), [submitQuery]);

  const handleInputKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setServerPopoverOpen(true);
      if (options.length > 0) {
        setActiveIndex(
          highlightedIndex < 0 || highlightedIndex >= options.length - 1 ? 0 : highlightedIndex + 1
        );
      }
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      setServerPopoverOpen(true);
      if (options.length > 0) {
        setActiveIndex(highlightedIndex <= 0 ? options.length - 1 : highlightedIndex - 1);
      }
      return;
    }

    if (event.key === "Enter") {
      event.preventDefault();
      if (listOpen && highlightedIndex >= 0) {
        selectOption(options[highlightedIndex]);
        return;
      }
      void form.handleSubmit(onSubmit)();
      return;
    }

    if (event.key === "Escape") {
      if (listOpen) {
        event.preventDefault();
        setServerPopoverOpen(false);
        setActiveIndex(-1);
      }
    }
  };

  const fieldError = form.formState.errors.query?.message;
  const inlineError = fieldError ?? (lookupError ? errorMessage(lookupError) : undefined);

  const optionClassName = (index: number) =>
    cn(lookupRowClassName, "cursor-pointer", index === highlightedIndex && "border-border bg-accent");

  return (
    <Popover
      open={listOpen}
      onOpenChange={open => {
        if (!open) {
          setServerPopoverOpen(false);
          setActiveIndex(-1);
        } else {
          setServerPopoverOpen(true);
        }
      }}
    >
      <PopoverAnchor asChild>
        <form role="search" onSubmit={form.handleSubmit(onSubmit)} className="flex w-full flex-col gap-2">
          <div className="flex flex-col items-center gap-2 md:flex-row md:gap-0">
            <InputGroup className={cn("w-full", className)}>
              <Controller
                name="query"
                control={form.control}
                render={({ field }) => (
                  <InputGroupInput
                    {...field}
                    ref={node => {
                      field.ref(node);
                      inputRef.current = node;
                    }}
                    type="text"
                    role="combobox"
                    autoComplete="off"
                    aria-autocomplete="list"
                    aria-expanded={listOpen}
                    aria-controls={listboxId}
                    aria-activedescendant={
                      listOpen && highlightedIndex >= 0 ? optionId(highlightedIndex) : undefined
                    }
                    aria-busy={isSearching}
                    placeholder={placeholder}
                    className="text-base md:text-sm"
                    aria-invalid={!!inlineError}
                    aria-describedby={inlineError ? errorId : undefined}
                    disabled={isLookupLoading}
                    onFocus={() => setServerPopoverOpen(true)}
                    onKeyDown={handleInputKeyDown}
                    onChange={e => {
                      field.onChange(e);
                      setActiveIndex(-1);
                      form.clearErrors("query");
                      setServerPopoverOpen(true);
                      if (lookupError) clearError();
                    }}
                  />
                )}
              />

              <InputGroupAddon>
                {isLookupLoading ? (
                  <Loader2 className="text-muted-foreground size-4 animate-spin" />
                ) : (
                  <Search className="text-muted-foreground size-4" />
                )}
              </InputGroupAddon>

              <InputGroupAddon align="inline-end" className="min-w-9">
                <InputGroupButton
                  type="button"
                  size="icon-xs"
                  variant="ghost"
                  aria-label="Clear search"
                  disabled={isLookupLoading}
                  className={cn(queryValue.length === 0 && "pointer-events-none invisible")}
                  onClick={() => {
                    setActiveIndex(-1);
                    form.setValue("query", "");
                    form.clearErrors("query");
                    clearError();
                    inputRef.current?.focus();
                  }}
                >
                  <X className="size-4" />
                </InputGroupButton>
              </InputGroupAddon>
            </InputGroup>

            {showSubmit && (
              <Button
                type="submit"
                className="block w-full text-xs md:hidden md:text-sm"
                disabled={isLookupLoading}
              >
                Search
              </Button>
            )}

            <ServerEditionDialog
              open={serverDialogOpen}
              onOpenChange={setServerDialogOpen}
              serverAddress={pendingServer}
              onSelectEdition={handleServerEdition}
            />
          </div>

          {inlineError && (
            <p id={errorId} role="alert" className="text-danger px-1 text-left text-xs leading-snug">
              {inlineError}
            </p>
          )}
        </form>
      </PopoverAnchor>
      <PopoverContent
        className="max-h-72 w-(--radix-popover-trigger-width) min-w-(--radix-popover-trigger-width) overflow-y-auto p-0"
        align="start"
        onOpenAutoFocus={e => e.preventDefault()}
        onCloseAutoFocus={e => e.preventDefault()}
      >
        <div
          id={listboxId}
          role="listbox"
          aria-label="Search suggestions"
          className="flex flex-col gap-1 p-1"
        >
          {showHistoryDropdown ? (
            <div role="group" aria-label="Recent searches" className="flex flex-col gap-1">
              <div className="text-muted-foreground flex items-center gap-2 px-3 py-1.5">
                <Clock className="size-3.5 shrink-0" aria-hidden />
                <span className="text-xs font-medium">Recent</span>
              </div>
              {recentOptions.map((option, index) =>
                option.kind === "recent" ? (
                  <div
                    key={option.key}
                    id={optionId(index)}
                    role="option"
                    aria-selected={highlightedIndex === index}
                    className={optionClassName(index)}
                    onMouseDown={event => event.preventDefault()}
                    onMouseEnter={() => setActiveIndex(index)}
                    onClick={() => selectOption(option)}
                  >
                    <Clock className="text-muted-foreground size-3.5 shrink-0" aria-hidden />
                    <span className="min-w-0 flex-1 truncate">{option.entry.query}</span>
                  </div>
                ) : null
              )}
            </div>
          ) : null}

          {!showHistoryDropdown && playerOptions.length > 0 ? (
            <div role="group" aria-label="Players" className="flex flex-col gap-1">
              <div className="text-muted-foreground flex items-center gap-2 px-3 py-1.5">
                <User className="size-3.5 shrink-0" aria-hidden />
                <span className="text-xs font-medium">Players</span>
              </div>
              {playerOptions.map((option, index) =>
                option.kind === "player" ? (
                  <div
                    key={option.key}
                    id={optionId(index)}
                    role="option"
                    aria-selected={highlightedIndex === index}
                    className={optionClassName(index)}
                    onMouseDown={event => event.preventDefault()}
                    onMouseEnter={() => setActiveIndex(index)}
                    onClick={() => selectOption(option)}
                  >
                    <Image
                      src={imageUrl(option.entry.skin.parts.FACE, { size: 64 })}
                      alt=""
                      className="size-8 shrink-0 rounded-md object-cover"
                      width={32}
                      height={32}
                    />
                    <span className="min-w-0 flex-1 truncate font-medium">{option.entry.username}</span>
                  </div>
                ) : null
              )}
            </div>
          ) : null}

          {!showHistoryDropdown && serverOptions.length > 0 ? (
            <div role="group" aria-label="Servers" className="flex flex-col gap-1">
              <div className="text-muted-foreground flex items-center gap-2 px-3 py-1.5">
                <Server className="size-3.5 shrink-0" aria-hidden />
                <span className="text-xs font-medium">Servers</span>
              </div>
              {serverOptions.map((option, index) =>
                option.kind === "server" ? (
                  <div
                    key={option.key}
                    id={optionId(playerOptions.length + index)}
                    role="option"
                    aria-selected={highlightedIndex === playerOptions.length + index}
                    className={optionClassName(playerOptions.length + index)}
                    onMouseDown={event => event.preventDefault()}
                    onMouseEnter={() => setActiveIndex(playerOptions.length + index)}
                    onClick={() => selectOption(option)}
                  >
                    <span className="min-w-0 flex-1 truncate font-medium">{option.entry.displayName}</span>
                    <span className="text-muted-foreground shrink-0 truncate text-xs">
                      {option.entry.hostnames[0]} · {option.entry.platform}
                    </span>
                  </div>
                ) : null
              )}
            </div>
          ) : null}

          {isSearching && options.length === 0 ? (
            <div
              role="presentation"
              className="text-muted-foreground flex items-center gap-2 px-3 py-2 text-xs"
            >
              <Loader2 className="size-3.5 animate-spin" aria-hidden />
              Searching…
            </div>
          ) : null}
        </div>
        {showHistoryDropdown ? (
          <div className="border-border border-t p-1">
            <button
              type="button"
              className="text-muted-foreground hover:text-foreground hover:bg-control-hover focus-visible:outline-ring w-full rounded-sm px-3 py-2 text-left text-xs outline-none focus-visible:outline-2 focus-visible:outline-offset-2"
              onClick={() => {
                setHistory([]);
                setActiveIndex(-1);
              }}
            >
              Clear recent searches
            </button>
          </div>
        ) : null}
      </PopoverContent>
    </Popover>
  );
}
