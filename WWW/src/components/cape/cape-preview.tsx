import { capeRenderUrl } from "@/common/mc-utils";
import { DownloadIcon } from "lucide-react";
import type { Cape } from "mcutils-js-api";
import Card, { CardContent } from "../ui/card";
import DownloadFileButton from "../ui/download-file-button";

interface CapePreviewProps {
  cape: Cape;
}

export default function CapePreview({ cape }: CapePreviewProps) {
  const capeName = cape.name ?? "Unknown Cape";

  return (
    <Card className="min-h-105 w-full">
      <CardContent className="group relative flex items-center justify-center p-4">
        <img src={capeRenderUrl(cape.id)} alt={capeName} className="h-96" />
        <div className="absolute top-2 right-2">
          <DownloadFileButton
            href={cape.textureUrl}
            filename={`${cape.textureId}.png`}
            label={`Download ${capeName} texture`}
          >
            <DownloadIcon className="size-4" />
          </DownloadFileButton>
        </div>
      </CardContent>
    </Card>
  );
}
