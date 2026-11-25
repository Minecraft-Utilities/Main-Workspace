import { Award, Clock, TrendingUpIcon } from "lucide-react";
import type { SkinSort } from "mcutils-js-api";
import type { ReactNode } from "react";

export const SKIN_SORT_OPTIONS = {
  TRENDING: { label: "Trending", icon: <TrendingUpIcon /> },
  TOP: { label: "Top", icon: <Award /> },
  LATEST: { label: "Latest", icon: <Clock /> },
} satisfies Record<SkinSort, { label: string; icon: ReactNode }>;

export function isSkinSort(value: string | undefined): value is SkinSort {
  return value !== undefined && Object.keys(SKIN_SORT_OPTIONS).includes(value);
}
