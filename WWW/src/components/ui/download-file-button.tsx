"use client";

import { Loader2 } from "lucide-react";
import { useState } from "react";
import { Button } from "./button";
import { Tooltip } from "./tooltip";

export interface DownloadFileButtonProps {
  children: React.ReactNode;
  href: string;
  filename?: string;
  /** Accessible name, also shown as the tooltip. */
  label: string;
}

export default function DownloadFileButton({ children, href, filename, label }: DownloadFileButtonProps) {
  const [isDownloading, setIsDownloading] = useState(false);

  const handleDownload = async () => {
    setIsDownloading(true);
    const response = await fetch(href);
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename ?? href;
    a.click();
    setIsDownloading(false);
  };

  return (
    <Tooltip content={<p>{label}</p>}>
      <Button
        variant="outline"
        size="icon"
        aria-label={label}
        onClick={handleDownload}
        disabled={isDownloading}
      >
        {isDownloading ? <Loader2 className="size-4 animate-spin" /> : children}
      </Button>
    </Tooltip>
  );
}
