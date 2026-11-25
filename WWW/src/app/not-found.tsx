import Background from "@/components/background";
import { Button } from "@/components/ui/button";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Page Not Found",
  robots: { index: false, follow: true },
};

const toolLinks = [
  { href: "/skins", label: "Skins" },
  { href: "/capes", label: "Capes" },
  { href: "/names", label: "Names" },
  { href: "/ip", label: "IP lookup" },
];

export default function NotFoundPage() {
  return (
    <>
      <Background />
      <div className="relative flex w-full flex-col items-center justify-center pt-24 text-center">
        <div className="z-10 flex flex-col items-center gap-8">
          <div className="flex flex-col items-center gap-4">
            <h1 className="text-foreground text-2xl leading-8 font-[650] tracking-[-0.02em] sm:text-[28px] sm:leading-9">This Page Doesn&apos;t Exist</h1>
            <p className="text-text-secondary max-w-md text-sm sm:text-base">
              The page you&apos;re looking for doesn&apos;t exist or has moved. Try one of the tools below.
            </p>
          </div>

          <div className="grid w-full grid-cols-2 gap-3 sm:flex sm:w-auto sm:flex-wrap sm:justify-center">
            {toolLinks.map(link => (
              <Button key={link.href} asChild size="lg" variant="outline">
                <Link href={link.href}>{link.label}</Link>
              </Button>
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
