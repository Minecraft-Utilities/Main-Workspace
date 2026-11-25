import Card, { CardContent } from "@/components/ui/card";
import { AtSignIcon, Globe2Icon, ScanFaceIcon, ShirtIcon } from "lucide-react";
import Link from "next/link";

const tools = [
  {
    name: "Capes",
    description: "Every cape and who owns it.",
    href: "/capes",
    icon: ShirtIcon,
  },
  {
    name: "Skins",
    description: "Trending and latest skins.",
    href: "/skins",
    icon: ScanFaceIcon,
  },
  {
    name: "Names",
    description: "Live username changes.",
    href: "/names",
    icon: AtSignIcon,
  },
  {
    name: "IP lookup",
    description: "Location and network details.",
    href: "/ip",
    icon: Globe2Icon,
  },
];

export default function ToolCards() {
  return (
    <div className="grid w-full grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4 lg:grid-cols-4">
      {tools.map(tool => {
        const Icon = tool.icon;

        return (
          <Link
            key={tool.href}
            href={tool.href}
            className="group block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            <Card className="h-full transition-[transform,border-color] duration-[120ms] ease-out group-hover:-translate-y-0.5 group-hover:border-border-strong group-focus-visible:border-border-strong">
              <CardContent className="flex h-full flex-row items-start gap-3 sm:flex-col">
                <Icon className="text-muted-foreground mt-0.5 size-5 shrink-0 sm:mt-0" aria-hidden />
                <div className="flex flex-col gap-1">
                  <p className="text-foreground text-sm font-semibold">{tool.name}</p>
                  <p className="text-text-secondary text-[13px] leading-snug">{tool.description}</p>
                </div>
              </CardContent>
            </Card>
          </Link>
        );
      })}
    </div>
  );
}
