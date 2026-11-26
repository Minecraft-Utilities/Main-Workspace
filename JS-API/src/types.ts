import type { components } from "./generated/schema";

type Schemas = components["schemas"];

export type ErrorResponse = Schemas["ErrorResponse"];
export type BasicPlayer = Schemas["BasicPlayer"];
export type FullPlayer = Schemas["FullPlayer"];
export type BedrockPlayer = Schemas["BedrockPlayer"];
export type BedrockSkin = Schemas["BedrockSkin"];
export type Skin = Schemas["Skin"];
export type Cape = Schemas["VanillaCape"];
export type UsernameHistory = Schemas["UsernameHistory"];
export type RecentUsernameChange = Schemas["RecentUsernameChange"];
export type IpLookup = Schemas["IpLookup"];
export type GeoLocation = Schemas["GeoLocation"];
export type AsnLookup = Schemas["AsnLookup"];
export type Statistics = Schemas["StatisticsResponse"];
export type ServerRegistryEntry = Schemas["ServerRegistryEntry"];
export type ARecord = Schemas["ARecord"];
export type SrvRecord = Schemas["SRVRecord"];
export type DnsRecord = ARecord | SrvRecord;
export type SkinPage = Schemas["PageSkin"];

type ServerWithRecords<T> = Omit<Schemas["CachedMinecraftServer"] & T, "records"> & { records: DnsRecord[] };

export type JavaServer = ServerWithRecords<Schemas["JavaMinecraftServer"]>;
export type BedrockServer = ServerWithRecords<Schemas["BedrockMinecraftServer"]>;

export type Platform = Schemas["ServerRegistryEntry"]["platform"];
export type PlayerType = "BASIC" | "FULL";
export type SkinSort = "TRENDING" | "LATEST" | "TOP";
export type SkinPart = "FACE" | "BODY" | "BACK" | "HEAD_ISO" | "FULLBODY_ISO_FRONT" | "FULLBODY_ISO_BACK";
export type CapePart = "FRONT" | "ISO";

export type ImageOptions = {
  size?: number;
};

export type SkinImageOptions = ImageOptions & {
  overlays?: boolean;
  /**
   * Texture id of a cape to render alongside the skin (full-body isometric parts only).
   */
  capeId?: string;
};

export type BedrockSkinImageOptions = Omit<SkinImageOptions, "capeId">;

/**
 * Either the data or the error, never both. Narrow with `if (result.error)`.
 */
export type Result<T> = { data: T; error?: undefined } | { data?: undefined; error: ErrorResponse };
