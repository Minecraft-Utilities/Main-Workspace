"use client";

import { cn } from "@/common/utils";
import OwnerCount from "@/components/owner-count";
import Card, { CardContent } from "@/components/ui/card";
import Image from "next/image";
import Link from "next/link";
import { useState } from "react";

export interface MediaCardProps {
  href: string;
  imageUrl: string;
  alt: string;
  caption?: string;
  count?: number;
  className?: string;
}

export function MediaCard({ href, imageUrl, alt, caption, count, className }: MediaCardProps) {
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <Link
      href={href}
      className="group focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring block w-full rounded-lg"
    >
      <Card
        className={cn(
          "overflow-hidden transition-[transform,border-color] duration-[120ms] ease-out group-hover:-translate-y-0.5 group-hover:border-border-strong group-focus-visible:border-border-strong",
          className
        )}
      >
        <CardContent className="flex min-h-0 flex-1 flex-col p-0">
          <div className="bg-stage bg-[radial-gradient(circle_at_50%_42%,var(--stage-highlight),var(--stage)_72%)] relative flex aspect-[3/4] w-full items-center justify-center p-4">
            {imageFailed || !imageUrl ? (
              <div className="text-text-faint flex flex-col items-center gap-2">
                <div aria-hidden className="bg-control h-16 w-10 rounded-sm" />
                <span className="text-xs">No preview</span>
              </div>
            ) : (
              <Image
                src={imageUrl}
                alt={alt || caption || "Minecraft game art"}
                width={184}
                height={184}
                unoptimized
                onError={() => setImageFailed(true)}
                className="pixelated max-h-full max-w-full w-auto object-contain"
              />
            )}
          </div>
          <div className="flex flex-col gap-0.5 px-3 py-2.5">
            {caption ? (
              <p className="text-foreground truncate text-sm font-semibold">{caption}</p>
            ) : null}
            {count !== undefined ? (
              <OwnerCount count={count} name="owners" ariaHidden className="tabular-nums" />
            ) : null}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
