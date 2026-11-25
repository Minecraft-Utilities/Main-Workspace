"use client";

import Image from "next/image";

export const DEFAULT_BACKGROUND = "/media/backgrounds/landing.webp";

interface BackgroundProps {
  url?: string;
}

export default function Background({ url = DEFAULT_BACKGROUND }: BackgroundProps) {
  return (
    <div
      aria-hidden
      className="bg-background pointer-events-none absolute inset-x-0 top-0 -z-10 h-[200px] overflow-hidden [mask-image:linear-gradient(to_bottom,black_30%,transparent)] select-none md:h-[280px]"
    >
      <Image
        src={url}
        alt=""
        fill
        sizes="100vw"
        priority
        className="scale-105 object-cover object-top opacity-35 blur-[8px]"
      />
    </div>
  );
}
