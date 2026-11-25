import SimpleLink from "@/components/simple-link";
import Card, { CardContent } from "@/components/ui/card";
import { AtSignIcon, ScanFaceIcon, ShirtIcon } from "lucide-react";

const tools = [
  {
    name: "Capes",
    description: "Browse every known cape and the players who own it.",
    href: "/capes",
    icon: ShirtIcon,
  },
  {
    name: "Skins",
    description: "Explore trending skins and search the full collection.",
    href: "/skins",
    icon: ScanFaceIcon,
  },
  {
    name: "Names",
    description: "Track the latest Minecraft username changes.",
    href: "/names",
    icon: AtSignIcon,
  },
];

export default function ToolCards() {
  return (
    <div className="grid w-full max-w-xl grid-cols-1 gap-3 sm:grid-cols-3">
      {tools.map(tool => {
        const Icon = tool.icon;
        return (
          <SimpleLink key={tool.href} href={tool.href} className="block">
            <Card className="h-full transition-transform duration-200 hover:-translate-y-0.5">
              <CardContent className="flex flex-col gap-2">
                <Icon className="text-primary size-5" aria-hidden />
                <div>
                  <p className="text-foreground text-sm font-semibold">{tool.name}</p>
                  <p className="text-muted-foreground text-xs leading-snug">{tool.description}</p>
                </div>
              </CardContent>
            </Card>
          </SimpleLink>
        );
      })}
    </div>
  );
}
