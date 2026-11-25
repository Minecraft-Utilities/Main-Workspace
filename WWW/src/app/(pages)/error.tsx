"use client";

import { errorMessage } from "@/common/error-message";
import Background from "@/components/background";
import { Button } from "@/components/ui/button";
import { HomeIcon } from "lucide-react";
import Link from "next/link";
import { useEffect } from "react";

export default function PagesError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <>
      <Background />
      <div className="relative flex w-full flex-col items-center justify-center pt-24 text-center">
        <div className="z-10 flex flex-col items-center gap-8">
          <div className="flex flex-col items-center gap-4">
            <h1 className="text-foreground text-2xl leading-8 font-[650] tracking-[-0.02em] sm:text-[28px] sm:leading-9">This Page Couldn&apos;t Load</h1>
            <p className="text-text-secondary max-w-md text-base">{errorMessage(error)}</p>
          </div>
          <div className="flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
            <Button size="lg" variant="outline" onClick={reset}>
              Try again
            </Button>
            <Button asChild size="lg">
              <Link href="/">
                <HomeIcon className="size-5" />
                Return Home
              </Link>
            </Button>
          </div>
        </div>
      </div>
    </>
  );
}
