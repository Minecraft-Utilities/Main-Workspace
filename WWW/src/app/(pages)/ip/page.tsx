import { buildMetadata } from "@/common/seo";
import Background from "@/components/background";
import IpSearch from "@/components/ip/ip-search";
import { PageShell, PageTitle } from "@/components/page-shell";
import { Metadata } from "next";
import { headers } from "next/headers";
import Link from "next/link";

export const metadata: Metadata = buildMetadata({
  title: "IP Lookup",
  description: "Look up the location, time zone, and network of any IPv4 or IPv6 address.",
  path: "/ip",
});

export const dynamic = "force-dynamic";

const EXAMPLES = ["8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"];

/** Loopback addresses say nothing useful about the visitor, so they never get a "Your IP" chip. */
function isLoopback(ip: string): boolean {
  const address = ip.toLowerCase();
  return address === "::1" || address.startsWith("127.") || address.startsWith("::ffff:127.");
}

export default async function IpIndexPage() {
  const forwarded = (await headers()).get("x-forwarded-for")?.split(",")[0]?.trim();
  const yourIp = forwarded && !isLoopback(forwarded) ? forwarded : undefined;
  const examples = Array.from(new Set([...(yourIp ? [yourIp] : []), ...EXAMPLES]));

  return (
    <PageShell>
      <Background />
      <PageTitle description="Find where an IP address is located and which network owns it.">
        IP Lookup
      </PageTitle>
      <div className="flex w-full flex-col gap-4">
        <IpSearch />
        <div className="text-muted-foreground flex flex-wrap items-center justify-center gap-2 text-xs sm:justify-start">
          <span>Try:</span>
          {examples.map(ip => (
            <Link
              key={ip}
              href={`/ip/${encodeURIComponent(ip)}`}
              className="bg-control border-border hover:bg-control-hover hover:text-foreground text-muted-foreground rounded-sm border px-2.5 py-2 font-mono md:px-2 md:py-1 transition-colors duration-[120ms]"
            >
              {ip === yourIp ? `Your IP (${ip})` : ip}
            </Link>
          ))}
        </div>
      </div>
    </PageShell>
  );
}
