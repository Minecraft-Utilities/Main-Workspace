"use client";

import type { Skin } from "mcutils-js-api";
import { createContext, ReactNode, useContext, useState } from "react";

type SelectedSkin = Pick<Skin, "textureId" | "textureUrl" | "parts">;

type SelectedSkinContextProps = {
  selectedSkin: SelectedSkin;
  setSelectedSkin: (skin: SelectedSkin) => void;
  hoveredSkin: SelectedSkin | null;
  setHoveredSkin: (skin: SelectedSkin | null) => void;
};
const SelectedSkinContext = createContext<SelectedSkinContextProps | undefined>(undefined);

export const SelectedSkinProvider = ({
  children,
  initialSkin,
}: {
  children: ReactNode;
  initialSkin: SelectedSkin;
}) => {
  const [selectedSkin, setSelectedSkin] = useState<SelectedSkin>(initialSkin);
  const [hoveredSkin, setHoveredSkin] = useState<SelectedSkin | null>(null);

  return (
    <SelectedSkinContext.Provider value={{ selectedSkin, setSelectedSkin, hoveredSkin, setHoveredSkin }}>
      {children}
    </SelectedSkinContext.Provider>
  );
};

export const useSelectedSkin = (): SelectedSkinContextProps => {
  const context = useContext(SelectedSkinContext);
  if (!context) {
    throw new Error("useSelectedSkin must be used within a SelectedSkinProvider");
  }
  return context;
};
