"use client";

import { Button } from "@/components/ui/button";
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import { Globe } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

export default function IpSearch({ defaultValue = "" }: { defaultValue?: string }) {
  const router = useRouter();
  const [value, setValue] = useState(defaultValue);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = value.trim();
    if (trimmed) {
      router.push(`/ip/${encodeURIComponent(trimmed)}`);
    }
  }

  return (
    <form role="search" onSubmit={onSubmit} className="flex w-full items-center gap-2">
      <InputGroup className="w-full">
        <InputGroupInput
          value={value}
          onChange={e => setValue(e.target.value)}
          placeholder="Enter an IPv4 or IPv6 address"
          aria-label="IP address"
          spellCheck={false}
          autoComplete="off"
        />
        <InputGroupAddon>
          <Globe className="text-muted-foreground size-4" />
        </InputGroupAddon>
      </InputGroup>
      <Button type="submit" className="shrink-0 text-xs md:text-sm">
        Lookup
      </Button>
    </form>
  );
}
