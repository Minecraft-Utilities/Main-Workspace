import { DownloadIcon } from "lucide-react";
import type { Skin } from "mcutils-js-api";
import { imageUrl } from "mcutils-js-api";
import Card from "../ui/card";
import DownloadFileButton from "../ui/download-file-button";

export interface SkinPreviewProps {
  skin: Skin;
}

export default function SkinPreview({ skin }: SkinPreviewProps) {
  const wornBy = skin.firstSeenUsing ? `Skin worn by ${skin.firstSeenUsing}` : "Minecraft skin render";

  return (
    <Card className="w-full overflow-hidden p-0">
      <div className="bg-stage relative flex min-h-105 items-center justify-center bg-[radial-gradient(circle_at_50%_42%,var(--stage-highlight),var(--stage)_72%)] p-4">
        <img
          src={imageUrl(skin.parts.FULLBODY_ISO_FRONT, { size: 768 })}
          alt={wornBy}
          className="pixelated h-96 max-w-full object-contain"
        />
        <div className="absolute top-2 right-2">
          <DownloadFileButton
            href={skin.textureUrl}
            filename={`${skin.textureId}.png`}
            label="Download skin texture"
          >
            <DownloadIcon className="size-4" />
          </DownloadFileButton>
        </div>
      </div>
    </Card>
  );
}
