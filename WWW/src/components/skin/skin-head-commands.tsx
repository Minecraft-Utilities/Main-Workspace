"use client";

import { env } from "@/common/env";
import { encodeSkinTextures } from "@/common/skin";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import type { Skin } from "mcutils-js-api";
import { CheckIcon, CopyIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "../ui/button";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "../ui/select";

interface SkinHeadCommandsProps {
  skin: Skin;
}

export default function SkinHeadCommands({ skin }: SkinHeadCommandsProps) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [copied, setCopied] = useState(false);
  const resetTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const commands = [
    {
      version: "1.20.5",
      command: `/give @p minecraft:player_head[profile={id:[I;-1507287714,1750554916,-1225423999,352299395],properties:[{name:"textures",value:"${encodeSkinTextures(skin.textureId)}"}]},minecraft:lore=['{"text":"${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}"}']]`,
    },
    {
      version: "1.13",
      command: `/give @p minecraft:player_head{SkullOwner:{Id:a6289d5e-6857-5924-b6f5-838114ffa983,Properties:{textures:[{Value:"${encodeSkinTextures(skin.textureId)}"}]}},display:{Lore:["{"text":"${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}"}"]}}`,
    },
    {
      version: "1.8",
      command: `/give @p minecraft:skull 1 3 {SkullOwner:{Id:a6289d5e-6857-5924-b6f5-838114ffa983,Properties:{textures:[{Value:"${encodeSkinTextures(skin.textureId)}"}]}},display:{Lore:["${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}"]}}`,
    },
  ];

  const command = commands[selectedIndex].command;

  useEffect(() => {
    return () => {
      if (resetTimer.current) {
        clearTimeout(resetTimer.current);
      }
    };
  }, []);

  async function copyCommand() {
    try {
      await navigator.clipboard.writeText(command);
      setCopied(true);
      if (resetTimer.current) {
        clearTimeout(resetTimer.current);
      }
      resetTimer.current = setTimeout(() => setCopied(false), 2000);
      toast.success("Copied to your clipboard");
    } catch {
      toast.error("Failed to copy");
    }
  }

  return (
    <Card>
      <CardHeader>Head Commands (for Command Blocks)</CardHeader>
      <CardContent className="flex flex-row items-start">
        <Select value={selectedIndex.toString()} onValueChange={value => setSelectedIndex(Number(value))}>
          <SelectTrigger className="h-9 w-26.25 rounded-r-none">
            <SelectValue placeholder={commands[selectedIndex].version} />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {commands.map((entry, index) => (
                <SelectItem key={entry.version} value={index.toString()}>
                  {entry.version}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>

        <div
          role="textbox"
          tabIndex={0}
          aria-readonly="true"
          aria-label={`Minecraft ${commands[selectedIndex].version} head command`}
          className="border-input h-9 min-w-0 flex-1 overflow-x-auto border-y px-3 py-2 font-mono text-xs whitespace-nowrap select-all [scrollbar-width:none]"
        >
          {command}
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={copyCommand}
          aria-label={copied ? "Command copied" : "Copy command"}
          className="h-9 shrink-0 rounded-l-none px-3"
        >
          {copied ? <CheckIcon className="size-4" /> : <CopyIcon className="size-4" />}
          <span className="text-xs">{copied ? "Copied" : "Copy"}</span>
        </Button>
      </CardContent>
    </Card>
  );
}
