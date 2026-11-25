import { capeRenderUrl } from "@/common/mc-utils";
import { DownloadIcon } from "lucide-react";
import type { Cape } from "mcutils-js-api";
import Card from "../ui/card";
import DownloadFileButton from "../ui/download-file-button";

export interface CapePreviewProps {
  cape: Cape;
}

export default function CapePreview({ cape }: CapePreviewProps) {
  const capeName = cape.name ?? "Unknown cape";

  return (
    <Card className="w-full overflow-hidden p-0">
      <div className="bg-stage relative flex min-h-105 items-center justify-center bg-[radial-gradient(circle_at_50%_42%,var(--stage-highlight),var(--stage)_72%)] p-4">
        <img
          src={capeRenderUrl(cape.id)}
          alt={`${capeName} render`}
          className="pixelated h-96 max-w-full object-contain"
        />
        <div className="absolute top-2 right-2">
          <DownloadFileButton
            href={cape.textureUrl}
            filename={`${cape.textureId}.png`}
            label={`Download ${capeName} texture`}
          >
            <DownloadIcon className="size-4" />
          </DownloadFileButton>
        </div>
      </div>
    </Card>
  );
}
