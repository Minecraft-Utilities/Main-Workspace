import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { KeyValueList } from "@/components/ui/key-value-list";
import type { AsnLookup } from "mcutils-js-api";

export default function IpNetwork({ asn }: { asn: AsnLookup }) {
  return (
    <Card className="w-full min-w-0 overflow-hidden p-0">
      <CardHeader>Network</CardHeader>
      <CardContent>
        <KeyValueList
          groups={[
            {
              items: [
                { label: "Organization", value: asn.asnOrg, copyValue: asn.asnOrg },
                {
                  label: "ASN",
                  value: asn.asn,
                  copyValue: asn.asn,
                  tooltip: "The autonomous system number that announces this address.",
                },
                {
                  label: "CIDR",
                  value: asn.cidr,
                  copyValue: asn.cidr,
                  tooltip: "The network block this address belongs to.",
                },
              ],
            },
          ]}
        />
      </CardContent>
    </Card>
  );
}
