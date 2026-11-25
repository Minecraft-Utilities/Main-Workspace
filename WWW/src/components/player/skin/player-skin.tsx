"use client";

import { skinPartUrl } from "@/common/skin";
import { cn } from "@/common/utils";
import { useSelectedCape } from "@/components/provider/selected-cape-provider";
import { useSelectedSkin } from "@/components/provider/selected-skin-provider";
import { Skin3DSettingsProvider } from "@/components/provider/skin-3d-settings-provider";
import SimpleTooltip from "@/components/simple-tooltip";
import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import DownloadFileButton from "@/components/ui/download-file-button";
import { DownloadIcon } from "lucide-react";
import type { FullPlayer, SkinPart } from "mcutils-js-api";
import Image from "next/image";
import { useRef, useState } from "react";
import PlayerSkin3D from "./player-skin-3d";
import Skin3DButtons from "./skin-3d-buttons";

type SkinMode = "3D" | "2D";

const SKIN_PART_LABELS: Record<SkinPart, string> = {
  FULLBODY_ISO_FRONT: "Front",
  FULLBODY_ISO_BACK: "Back",
  HEAD_ISO: "Head",
  BACK: "Back (flat)",
  FACE: "Face",
  BODY: "Body",
};

export interface PlayerSkinProps {
  player: FullPlayer;
}

export default function PlayerSkin({ player }: PlayerSkinProps) {
  const { selectedSkin, hoveredSkin } = useSelectedSkin();
  const { selectedCape, hoveredCape } = useSelectedCape();

  const displaySkin = hoveredSkin ?? selectedSkin;
  const displayCape = hoveredCape ?? selectedCape;

  const [selectedMode, setSelectedMode] = useState<SkinMode>("2D");
  const [selectedPart, setSelectedPart] = useState<SkinPart>("FULLBODY_ISO_FRONT");
  const [hoveredPart, setHoveredPart] = useState<SkinPart | null>(null);
  const [hoveredMode, setHoveredMode] = useState<string | null>(null);
  const hoverClearTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  const displayMode = hoveredMode ?? selectedMode;
  const displayPart = hoveredPart ?? selectedPart;

  function handlePartHoverEnter(part: SkinPart) {
    if (hoverClearTimeout.current) clearTimeout(hoverClearTimeout.current);
    setHoveredPart(part);
    setHoveredMode("2D");
  }

  function handlePartHoverLeave() {
    hoverClearTimeout.current = setTimeout(() => {
      setHoveredPart(null);
      setHoveredMode(null);
    }, 80);
  }

  return (
    <Card className="overflow-hidden p-0">
      <CardHeader>Skin</CardHeader>
      <CardContent className="relative flex flex-col items-center gap-4">
        <div className="relative flex h-72 w-full items-center justify-center overflow-hidden">
          {displayMode === "2D" && (
            <SkinPreviewImage
              key={skinPartUrl(displaySkin.parts[displayPart], displayCape?.id, 576)}
              src={skinPartUrl(displaySkin.parts[displayPart], displayCape?.id, 576)}
              alt={`${player.username} skin - ${SKIN_PART_LABELS[displayPart]}`}
            />
          )}
          {displayMode === "3D" && (
            <Skin3DSettingsProvider player={player}>
              <PlayerSkin3D />
              <Skin3DButtons player={player} />
            </Skin3DSettingsProvider>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-center gap-2">
          <div className="size-10 shrink-0">
            <SimpleTooltip display="3D" className="size-full">
              <SkinSelectionButton selected={selectedMode === "3D"} onClick={() => setSelectedMode("3D")}>
                <p className="font-bold">3D</p>
              </SkinSelectionButton>
            </SimpleTooltip>
          </div>

          {Object.keys(displaySkin.parts)
            .sort(key => (key.includes("ISO") ? -1 : 1))
            .map(key => {
              const part = key as SkinPart;

              return (
                <div key={key} className="size-10 shrink-0">
                  <SimpleTooltip display={SKIN_PART_LABELS[part] ?? key} className="size-full">
                    <SkinSelectionButton
                      selected={selectedPart === part && selectedMode === "2D"}
                      onClick={() => {
                        setSelectedPart(part);
                        setSelectedMode("2D");
                      }}
                      onMouseEnter={() => handlePartHoverEnter(part)}
                      onMouseLeave={handlePartHoverLeave}
                    >
                      <Image
                        src={skinPartUrl(displaySkin.parts[part], displayCape?.id, 128)}
                        alt={SKIN_PART_LABELS[part] ?? part}
                        width={56}
                        height={56}
                        className="h-full w-full object-contain object-center p-1.5"
                      />
                    </SkinSelectionButton>
                  </SimpleTooltip>
                </div>
              );
            })}
        </div>

        {/* Download Buttons */}
        <div className="absolute top-2 right-2 flex gap-2">
          <DownloadFileButton
            href={displaySkin.textureUrl}
            filename={`${player.username}-skin-texture.png`}
            label="Download skin"
          >
            <DownloadIcon className="size-4" />
          </DownloadFileButton>
        </div>
      </CardContent>
    </Card>
  );
}

function SkinPreviewImage({ src, alt }: { src: string; alt: string }) {
  const [loaded, setLoaded] = useState(false);

  return (
    <>
      {!loaded && <Skeleton className="absolute inset-0 rounded-none" />}
      <Image
        src={src}
        alt={alt}
        width={256}
        height={256}
        loading="eager"
        onLoad={() => setLoaded(true)}
        style={{ width: "auto", height: "auto" }}
        className={cn(
          "relative max-h-full max-w-full object-contain transition-opacity duration-300",
          loaded ? "opacity-100" : "opacity-0"
        )}
      />
    </>
  );
}

interface SkinSelectionButtonProps {
  children: React.ReactNode;
  selected: boolean;
  onClick: () => void;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
}

function SkinSelectionButton({
  children,
  selected,
  onClick,
  onMouseEnter,
  onMouseLeave,
}: SkinSelectionButtonProps) {
  return (
    <button
      onClick={onClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      className={cn(
        "bg-muted/30 flex overflow-hidden rounded-lg border-2 transition-colors",
        "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
        "size-10 items-center justify-center",
        selected ? "border-primary" : "border-border/60 hover:border-border"
      )}
    >
      {children}
    </button>
  );
}
