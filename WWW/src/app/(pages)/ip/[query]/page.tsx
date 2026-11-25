import { isNotFoundError } from "@/common/mc-utils";
import { breadcrumbJsonLd, buildMetadata } from "@/common/seo";
import Background from "@/components/background";
import { ErrorCard } from "@/components/error-card";
import IpHeader from "@/components/ip/ip-header";
import IpLocation from "@/components/ip/ip-location";
import IpNetwork from "@/components/ip/ip-network";
import IpSearch from "@/components/ip/ip-search";
import { JsonLd } from "@/components/json-ld";
import { PageShell } from "@/components/page-shell";
import type { ErrorResponse } from "mcutils-js-api";
import { Metadata } from "next";
import { notFound } from "next/navigation";
import { getIp } from "./data";

const isMissing = (error: ErrorResponse | undefined) => isNotFoundError(error) || error?.code === 400;

export const dynamic = "force-dynamic";

export async function generateMetadata(props: PageProps<"/ip/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { data, error } = await getIp(query);
  if (!data && isMissing(error)) {
    notFound();
  }
  const ip = data?.ip ?? decodeURIComponent(query);
  const place = [data?.location?.city, data?.location?.country].filter(Boolean).join(", ");

  return buildMetadata({
    title: `${ip}: IP Lookup`,
    description: data
      ? `${ip}${place ? ` is located in ${place}` : ""}${data.asn ? ` and belongs to ${data.asn.asnOrg} (${data.asn.asn})` : ""}.`
      : `Look up the location and network of ${ip}.`,
    path: `/ip/${encodeURIComponent(ip)}`,
    noindex: !data,
  });
}

export default async function IpPage({ params }: PageProps<"/ip/[query]">) {
  const { query } = await params;
  const { data: lookup, error } = await getIp(query);

  if (!lookup) {
    if (isMissing(error)) {
      notFound();
    }
    return (
      <PageShell>
        <Background />
        <div className="w-full">
          <IpSearch defaultValue={decodeURIComponent(query)} />
        </div>
        <ErrorCard
          title="Lookup failed"
          message={error?.message ?? "This IP address could not be looked up. Try again in a moment."}
        />
      </PageShell>
    );
  }

  return (
    <PageShell>
      <Background />
      <JsonLd
        id="ip-jsonld"
        data={breadcrumbJsonLd([
          { name: "Home", path: "/" },
          { name: "IP Lookup", path: "/ip" },
          { name: lookup.ip, path: `/ip/${encodeURIComponent(lookup.ip)}` },
        ])}
      />
      <IpSearch defaultValue={lookup.ip} />
      <IpHeader lookup={lookup} />
      <div className="flex w-full min-w-0 flex-col gap-4">
        {lookup.location && <IpLocation location={lookup.location} />}
        {lookup.asn && <IpNetwork asn={lookup.asn} />}
        {!lookup.location && !lookup.asn && (
          <ErrorCard title="No data found" message="We have no location or network data for this address." />
        )}
      </div>
    </PageShell>
  );
}
