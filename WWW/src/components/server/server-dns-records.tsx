import type { DnsRecord, SrvRecord } from "mcutils-js-api";
import Card, { CardContent, CardHeader } from "../ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../ui/table";

function isSrvRecord(record: DnsRecord): record is SrvRecord {
  return record.type === "SRV";
}

export interface ServerDnsRecordsProps {
  records: DnsRecord[];
}

export default function ServerDnsRecords({ records }: ServerDnsRecordsProps) {
  if (!records || records.length === 0) {
    return null;
  }

  return (
    <section className="flex w-full min-w-0 flex-col gap-4">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardHeader>DNS records</CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader className="bg-card">
              <TableRow className="hover:bg-transparent">
                <TableHead className="pl-4">Hostname</TableHead>
                <TableHead>Type</TableHead>
                <TableHead className="pr-4">Data</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {records.map(record => {
                const rowKey = isSrvRecord(record)
                  ? `srv-${record.name}-${record.port}-${record.target}`
                  : `record-${record.name}-${record.type}-${record.address ?? ""}`;

                return (
                  <TableRow key={rowKey}>
                    <TableCell className="text-foreground pl-4 font-mono">
                      {record.name ?? "Unknown"}
                    </TableCell>
                    <TableCell className="text-foreground font-mono">{record.type}</TableCell>
                    <TableCell className="text-muted-foreground pr-4 font-mono">
                      {isSrvRecord(record)
                        ? `${record.priority} ${record.weight} ${record.port} ${record.target}`
                        : (record.address ?? "Unknown")}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </section>
  );
}
