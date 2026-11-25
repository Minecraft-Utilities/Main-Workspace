import { Button } from "@/components/ui/button";
import { HomeIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Page Not Found",
  robots: { index: false, follow: true },
};

export default function NotFoundPage() {
  return (
    <div className="relative flex w-full flex-col items-center justify-center pt-24 text-center">
      <div className="z-10 flex flex-col items-center gap-8">
        <div className="flex flex-col items-center gap-4">
          <h1 className="text-destructive text-3xl font-bold sm:text-4xl">Page Not Found</h1>
          <p className="text-muted-foreground max-w-md text-lg">
            The page you&apos;re looking for doesn&apos;t exist or has been moved. Don&apos;t worry, you can
            always find your way back!
          </p>
        </div>

        <Button asChild size="lg">
          <Link href="/">
            <HomeIcon className="size-5" />
            Return Home
          </Link>
        </Button>
      </div>
    </div>
  );
}
