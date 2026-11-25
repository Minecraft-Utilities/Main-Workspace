import { formatNumberWithCommas } from "@/common/utils";
import { UserIcon } from "lucide-react";

export default function OwnerCount({ count, name }: { count: number; name?: string }) {
  const formatted = formatNumberWithCommas(count);
  const label = name ? `${formatted} ${name}` : formatted;

  return (
    <div className="flex items-center gap-1" role="img" aria-label={label} title={label}>
      <UserIcon className="text-primary size-3.5" aria-hidden />
      <span className="text-xs text-white/60">
        {formatted}
        {name ? ` ${name}` : ""}
      </span>
    </div>
  );
}
