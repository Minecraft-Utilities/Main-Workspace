import { DownloadIcon } from "lucide-react";
import type { Skin } from "mcutils-js-api";
import Card, { CardContent } from "../ui/card";
import DownloadFileButton from "../ui/download-file-button";

interface SkinPreviewProps {
  skin: Skin;
}

export default function SkinPreview({ skin }: SkinPreviewProps) {
  return (
    <Card className="h-105 w-full">
      <CardContent className="group relative flex items-center justify-center p-4">
        <img src={skin.parts.FULLBODY_ISO_FRONT} alt={`Skin ${skin.id}`} className="h-96" />
        <div className="absolute top-2 right-2 opacity-0 transition-opacity group-hover:opacity-100">
          <DownloadFileButton href={skin.textureUrl} filename={`${skin.textureId}.png`}>
            <DownloadIcon className="size-4" />
          </DownloadFileButton>
        </div>
      </CardContent>
    </Card>
  );
}
