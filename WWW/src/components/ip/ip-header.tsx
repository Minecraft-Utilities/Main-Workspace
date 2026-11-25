import { BreakableText } from "@/components/breakable-text";
import { EntityHeader } from "@/components/ui/entity-header";
import { Globe } from "lucide-react";
import type { IpLookup } from "mcutils-js-api";
import { IpLocalTime } from "./ip-local-time";

export default function IpHeader({ lookup }: { lookup: IpLookup }) {
  const { location, asn } = lookup;
  const place = [location?.city, location?.region, location?.country].filter(Boolean).join(", ");
  const isV6 = lookup.ip.includes(":");

  return (
    <EntityHeader
      media={
        location?.flagUrl ? (
          <img
            src={location.flagUrl.replace("/w20/", "/w160/")}
            alt={`Flag of ${location.country}`}
            className="size-full object-contain p-1"
          />
        ) : (
          <Globe className="text-text-faint size-6 sm:size-8" aria-hidden />
        )
      }
      title={<BreakableText text={lookup.ip} />}
      badge={
        <span className="bg-control border-border text-muted-foreground rounded-sm border px-1.5 py-0.5 font-mono text-xs font-medium">
          {isV6 ? "IPv6" : "IPv4"}
        </span>
      }
      stats={[
        ...(place ? [{ label: "Location", value: place }] : []),
        ...(location?.timeZone
          ? [{ label: "Local time", value: <IpLocalTime timeZone={location.timeZone} /> }]
          : []),
        ...(asn ? [{ label: "Network", value: asn.asnOrg }] : []),
      ]}
    />
  );
}
