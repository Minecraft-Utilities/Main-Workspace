import { CopyButton } from "@/components/copy-button";

export interface PlayerUuidProps {
  uuid: string;
}

export default function PlayerUuid({ uuid }: PlayerUuidProps) {
  return (
    <div className="mt-1 flex items-center gap-1.5">
      <span className="text-muted-foreground font-mono text-sm break-all">{uuid}</span>
      <CopyButton variant="inline" value={uuid} label="Copy UUID">
        Copy
      </CopyButton>
    </div>
  );
}
