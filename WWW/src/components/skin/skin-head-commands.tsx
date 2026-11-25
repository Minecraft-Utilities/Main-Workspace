"use client";

import { env } from "@/common/env";
import { encodeSkinTextures } from "@/common/skin";
import { CopyButton } from "@/components/copy-button";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import type { Skin } from "mcutils-js-api";
import { useState } from "react";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "../ui/select";

interface SkinHeadCommandsProps {
  skin: Skin;
}

export default function SkinHeadCommands({ skin }: SkinHeadCommandsProps) {
  const [selectedIndex, setSelectedIndex] = useState(0);

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

  return (
    <Card>
      <CardHeader>Head commands</CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-center gap-2">
          <Select value={selectedIndex.toString()} onValueChange={value => setSelectedIndex(Number(value))}>
            <SelectTrigger className="h-9 min-w-0 flex-1" aria-label="Minecraft version">
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

          <CopyButton value={command} label="Copy command" variant="text" className="h-9 shrink-0 px-3">
            Copy
          </CopyButton>
        </div>

        <div
          role="textbox"
          tabIndex={0}
          aria-readonly="true"
          aria-label={`Minecraft ${commands[selectedIndex].version} head command`}
          className="border-border bg-control text-text-secondary focus-visible:outline-ring max-h-28 overflow-y-auto rounded-md border px-3 py-2 font-mono text-xs break-all select-all focus-visible:outline-2 focus-visible:outline-offset-2"
        >
          {command}
        </div>
      </CardContent>
    </Card>
  );
}
