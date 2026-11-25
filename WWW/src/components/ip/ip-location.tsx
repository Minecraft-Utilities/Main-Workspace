import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { KeyValueList, type KeyValueItem } from "@/components/ui/key-value-list";
import type { GeoLocation } from "mcutils-js-api";

function mapUrl(lat: number, lon: number): string {
  const dLon = 0.6;
  const dLat = 0.35;
  const bbox = [lon - dLon, lat - dLat, lon + dLon, lat + dLat].join("%2C");
  return `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${lat}%2C${lon}`;
}

export default function IpLocation({ location }: { location: GeoLocation }) {
  const { latitude, longitude } = location;
  const hasCoords = latitude != null && longitude != null;

  const items: KeyValueItem[] = [
    { label: "Country", value: `${location.country} (${location.countryCode})` },
    ...(location.region ? [{ label: "Region", value: location.region }] : []),
    ...(location.city ? [{ label: "City", value: location.city }] : []),
    ...(location.postalCode ? [{ label: "Postal code", value: location.postalCode }] : []),
    ...(hasCoords
      ? [
          {
            label: "Coordinates",
            value: `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`,
            copyValue: `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`,
          },
        ]
      : []),
    ...(location.timeZone ? [{ label: "Time zone", value: location.timeZone }] : []),
  ];

  return (
    <Card className="w-full min-w-0 overflow-hidden p-0">
      <CardHeader>Location</CardHeader>
      {hasCoords ? (
        <div className="border-border relative h-64 w-full border-b">
          <iframe
            title={`Map of ${location.city ?? location.country}`}
            src={mapUrl(latitude, longitude)}
            loading="lazy"
            className="size-full border-0 [filter:invert(0.92)_hue-rotate(180deg)_saturate(0.7)_brightness(0.9)]"
          />
          <div className="from-card/40 pointer-events-none absolute inset-0 bg-gradient-to-t to-transparent" />
        </div>
      ) : null}
      <CardContent>
        <KeyValueList groups={[{ items }]} />
      </CardContent>
    </Card>
  );
}
