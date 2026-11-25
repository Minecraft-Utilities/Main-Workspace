import { mcUtilsApi } from "@/common/mc-utils";
import { cache } from "react";

export const getIp = cache(async (query: string) => mcUtilsApi.ipLookup(decodeURIComponent(query)));
