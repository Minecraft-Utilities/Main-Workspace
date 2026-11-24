# Minecraft Utilities - JavaScript API

Fully typed TypeScript client for the [McUtils API](https://mc.fascinated.cc/api). The types are generated from the
backend's OpenAPI spec (`bun run generate`), so they always match what the API returns.

```bash
bun add mcutils-js-api
```

```typescript
import McUtils from "mcutils-js-api";

const mcutils = new McUtils();

const { data: player, error } = await mcutils.player().fetch("ImFascinated");
if (error) {
  console.error(error.message);
} else {
  console.log(player.username);
}

const full = await mcutils.player().fetch("ImFascinated", "FULL"); // FullPlayer
const server = await mcutils.server().fetchJava("aetheria.cc", 25565);
const face = await mcutils.player().skin("ImFascinated", "FACE", { size: 128 });
const url = mcutils.player().skinUrl("ImFascinated", "HEAD_ISO"); // for <img src>
```

Every call resolves to `{ data }` or `{ error }`, never throws.

## Regenerating the types

```bash
OPENAPI_URL=http://localhost:8080/v3/api-docs bun run generate
```
