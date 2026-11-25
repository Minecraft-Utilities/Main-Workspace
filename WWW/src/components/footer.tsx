"use client";

import SimpleLink from "@/components/simple-link";
import { ExternalLink } from "lucide-react";
import Image from "next/image";

type FooterLink = {
  name: string;
  href: string;
};

export const TOOL_LINKS: FooterLink[] = [
  { name: "Skins", href: "/skins" },
  { name: "Capes", href: "/capes" },
  { name: "Names", href: "/names" },
  { name: "IP lookup", href: "/ip" },
];

export const RESOURCE_LINKS: FooterLink[] = [
  { name: "Source Code", href: "https://github.com/Minecraft-Utilities" },
  {
    name: "Documentation",
    href: "https://mc.fascinated.cc/api/swagger-ui/index.html",
  },
  { name: "Server Registry", href: "https://github.com/Minecraft-Utilities/ServerRegistry" },
];

export default function Footer() {
  return (
    <footer className="border-border mt-10 shrink-0 border-t py-8 select-none">
      <div className="flex flex-col gap-8 md:flex-row md:justify-between md:gap-16">
        <div className="flex flex-col gap-3">
          <SimpleLink className="flex w-fit items-center gap-2.5" href="/" draggable={false}>
            <Image src="/media/logo/logo.png" alt="MC Utils" width={32} height={32} className="size-8" />
            <span className="text-foreground text-base font-bold">MC Utils</span>
          </SimpleLink>
          <p className="text-muted-foreground max-w-sm text-xs leading-relaxed">
            API for Minecraft player data (skins, capes, profiles), Java/Bedrock server status and previews.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-8 sm:gap-12">
          <FooterColumn title="Tools" links={TOOL_LINKS} />
          <FooterColumn title="Resources" links={RESOURCE_LINKS} />
        </div>
      </div>

      <div className="border-border text-muted-foreground mt-8 border-t pt-4 text-xs">
        Free and open source. Not affiliated with Mojang or Microsoft.
      </div>
    </footer>
  );
}

function FooterColumn({ title, links }: { title: string; links: FooterLink[] }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="text-foreground pb-1 text-sm font-semibold">{title}</h2>
      {links.map(link => {
        const external = !link.href.startsWith("/");
        return (
          <SimpleLink
            key={link.name}
            href={link.href}
            draggable={false}
            className="text-muted-foreground hover:text-foreground flex min-h-11 items-center gap-1.5 text-sm transition-colors md:min-h-0 md:py-1"
          >
            {link.name}
            {external && <ExternalLink className="size-3.5 shrink-0" aria-hidden />}
          </SimpleLink>
        );
      })}
    </div>
  );
}
