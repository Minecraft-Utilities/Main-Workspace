import { DownloadIcon } from "lucide-react";
import type { Skin } from "mcutils-js-api";
import { imageUrl } from "mcutils-js-api";
import Card, { CardContent } from "../ui/card";
import DownloadFileButton from "../ui/download-file-button";

interface SkinPreviewProps {
  skin: Skin;
}

export default function SkinPreview({ skin }: SkinPreviewProps) {
  const wornBy = skin.firstSeenUsing ? `Skin worn by ${skin.firstSeenUsing}` : "Minecraft skin";

  return (
    <Card className="min-h-105 w-full">
      <CardContent className="group relative flex items-center justify-center p-4">
        <img src={imageUrl(skin.parts.FULLBODY_ISO_FRONT, { size: 768 })} alt={wornBy} className="h-96" />
        <div className="absolute top-2 right-2">
          <DownloadFileButton
            href={skin.textureUrl}
            filename={`${skin.textureId}.png`}
            label="Download skin texture"
          >
            <DownloadIcon className="size-4" />
          </DownloadFileButton>
        </div>
      </CardContent>
    </Card>
  );
}
