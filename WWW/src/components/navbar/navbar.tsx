"use client";

import { cn } from "@/common/utils";
import type { LucideIcon } from "lucide-react";
import { AtSignIcon, ExternalLink, GlobeIcon, MenuIcon, ScanFaceIcon, ShirtIcon } from "lucide-react";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { type ReactNode, useState } from "react";
import { RESOURCE_LINKS, TOOL_LINKS } from "../footer";
import SimpleLink from "../simple-link";
import { Button } from "../ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from "../ui/dialog";
import QuerySearchNavbar from "./query-search-navbar";

const TOOL_ICONS: Record<string, LucideIcon> = {
  "/skins": ScanFaceIcon,
  "/capes": ShirtIcon,
  "/names": AtSignIcon,
  "/ip": GlobeIcon,
};

function isActivePath(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export default function Navbar() {
  const path = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <nav className="border-border bg-background/80 sticky inset-x-0 top-0 z-50 h-14 w-full border-b backdrop-blur-md select-none">
      <div className="mx-auto flex h-full w-full max-w-[1200px] items-center justify-between gap-2 px-4 md:px-6">
        <div className="flex min-w-0 items-center gap-2 md:gap-6">
          <SimpleLink className="flex h-10 min-w-10 shrink-0 items-center gap-2" href="/" draggable={false}>
            <Image width={28} height={28} className="size-7" src="/media/logo/logo.png" alt="MC Utils" />
            <span className="text-foreground hidden text-base font-bold sm:inline">MC Utils</span>
          </SimpleLink>

          <div className="hidden items-center gap-1 md:flex">
            {TOOL_LINKS.map(link => (
              <NavLink key={link.href} name={link.name} href={link.href} />
            ))}
          </div>
        </div>

        <div className="flex items-center gap-1">
          {path !== "/" && <QuerySearchNavbar />}

          <Dialog open={menuOpen} onOpenChange={setMenuOpen}>
            <DialogTrigger asChild>
              <Button variant="ghost" size="icon-lg" className="size-11 md:hidden" aria-label="Open menu">
                <MenuIcon className="size-5" aria-hidden />
              </Button>
            </DialogTrigger>
            <DialogContent className="border-border bg-background shadow-overlay inset-y-0 top-0 right-0 left-auto flex h-dvh w-[min(20rem,85vw)] max-w-none translate-x-0 translate-y-0 flex-col gap-0 rounded-none border-l p-0">
              <div className="border-border flex h-14 shrink-0 items-center border-b pr-12 pl-4">
                <DialogTitle className="text-foreground text-sm font-semibold">Menu</DialogTitle>
              </div>
              <DialogDescription className="sr-only">Site navigation and resources</DialogDescription>

              <nav className="flex flex-1 flex-col gap-6 overflow-y-auto p-4" aria-label="Mobile">
                <MenuSection title="Tools">
                  {TOOL_LINKS.map(link => {
                    const Icon = TOOL_ICONS[link.href];
                    return (
                      <MenuLink
                        key={link.href}
                        href={link.href}
                        active={isActivePath(path, link.href)}
                        onNavigate={() => setMenuOpen(false)}
                      >
                        {Icon && <Icon className="size-4 shrink-0" aria-hidden />}
                        {link.name}
                      </MenuLink>
                    );
                  })}
                </MenuSection>

                <MenuSection title="Resources">
                  {RESOURCE_LINKS.map(link => (
                    <MenuLink key={link.href} href={link.href} onNavigate={() => setMenuOpen(false)}>
                      {link.name}
                      <ExternalLink className="text-muted-foreground ml-auto size-3.5" aria-hidden />
                    </MenuLink>
                  ))}
                </MenuSection>
              </nav>
            </DialogContent>
          </Dialog>
        </div>
      </div>
    </nav>
  );
}

function NavLink({ name, href }: { name: string; href: string }) {
  const pathname = usePathname();
  const active = isActivePath(pathname, href);

  return (
    <SimpleLink
      href={href}
      draggable={false}
      className={cn(
        "hover:text-foreground flex h-14 items-center px-3 text-sm font-medium transition-colors",
        active ? "text-foreground shadow-[inset_0_-2px_0_var(--ring)]" : "text-muted-foreground"
      )}
    >
      {name}
    </SimpleLink>
  );
}

function MenuSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="text-foreground px-3 pb-1 text-sm font-semibold">{title}</h2>
      {children}
    </div>
  );
}

function MenuLink({
  href,
  active = false,
  onNavigate,
  children,
}: {
  href: string;
  active?: boolean;
  onNavigate: () => void;
  children: ReactNode;
}) {
  return (
    <SimpleLink
      href={href}
      draggable={false}
      onClick={onNavigate}
      className={cn(
        "hover:bg-control hover:text-foreground flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors",
        active ? "bg-selected text-foreground" : "text-muted-foreground"
      )}
    >
      {children}
    </SimpleLink>
  );
}
