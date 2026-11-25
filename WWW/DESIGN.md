# MC Utils design language: "Bedrock" (v2)

A dense, data-first dark UI built from Minecraft materials. Chrome is Deepslate, game art sits on neutral Stage, and colour only appears when it means something.

## 1. Audit summary (2026-11-25)

Covers 13 routes at 1440px and 390px, plus search, copy, 3D viewer, sort, filter, mobile search and keyboard focus, all driven through headless Chromium over CDP.

### What works and stays
- The dark base with a purple accent gives the site a recognisable brand.
- The rendered MOTD banner on server pages is the best component on the site.
- The search popover shows player heads and a recent-searches list. The mobile full-screen search with Cancel works well.
- Sort is link-driven (`/skins/top`), so it can be shared and works with the back button.
- The trending skin grid and the names live feed read well at a glance.

### Problems

**Foundation**
1. The whole UI, body copy included, is set in JetBrains Mono. Long text (the home blurb, descriptions) is slow to read, and nothing separates data from prose.
2. The font is loaded with `weight: "100 300"` (`src/app/layout.tsx:14`), but the UI renders weights 500–700. Those are synthesised faux-bold.
3. There are three container widths. The navbar is about 1150px, page content is 980px (`page-shell.tsx:13`) and the footer is full-bleed (`layout.tsx:85` max-w-400). Edges don't line up anywhere.
4. The neutrals mix three hues (49°, 84°, 34°). `--accent-deep`, `--accent-secondary`, `--chart-card`, the `--spacing-*` scale and five `--animate-*` tokens are dead. Status colours (`emerald-500`, `red-500`, `red-300`, a raw `hsl()` tooltip border, `#9770FF`) bypass tokens.
5. Every primitive has `dark:` variants even though no light theme exists.
6. `@heroicons/react`, `@radix-ui/react-icons`, `react-icons` and `recharts` are dependencies with zero imports. `ui/item`, `ui/table`, `ui/label`, `ui/separator` and `ui/textarea` are unused, and the DNS table hand-rolls a `<table>`.

**Layout and atmosphere**
7. The blurred screenshot backgrounds are page-specific and cover the whole viewport. IP lookup, 404 and error states have none, so the site feels like two products.
8. On the player page, the name-history panel spans a 600px column holding one row, while everything else stacks in the narrow left column.
9. On the skin detail page (`/skin/333217`), 763 owner chips render inline (250 DOM images, half still blank while loading). The page is 4,350px tall, and the useful head-command panel sits at the very bottom.
10. On the IP result page, a "Network" summary tile repeats the "Network" panel directly below it.
11. On `/capes`, the last 4 tiles (Translator (Japanese), Spade, Turtle, Millionth Customer) render an empty box with no fallback art.
12. Panel headings are re-implemented 8× as uppercase letter-spaced micro labels. The footer headings are purple while every other heading is white.

**Interaction and states**
13. Raw backend errors leak to users. Searching `!!! not valid ###` shows `Invalid UUID string '!!! not valid ###'`. An unresolvable host shows "IP address is in a blacklisted subnet", which is also the wrong message for a DNS failure.
14. An unknown player shows the generic "Page Not Found" with no search box or suggestions. The 404 title is error-red, which reads as a crash.
15. The search popover uses `role="listbox"` with non-option children and hardcoded `aria-selected={false}`. There is no arrow-key navigation, and Enter ignores the suggestions (`Not` + Enter goes to `/player/Not`).
16. Six skin-pose buttons and the 3D-viewer side buttons have no accessible name. Two unnamed `DIV`s take tab stops on the home page. 250 avatar images per detail page have no `alt`.
17. Focus is a 1px auto outline on links and nothing visible on cards.
18. Toasts render in Geist (Sonner's default) while the rest of the site is mono.
19. On mobile, 20 tap targets per page are under 40px. Inputs use `text-xs`, so iOS zooms in on focus. The nav is icon-only with no labels. IP lookup is missing from both the navbar and the footer.
20. `useIsMobile` and Tailwind's `md:` disagree at exactly 768px (`viewport-context.tsx:84`). The dev hint "Your IP (::1)" is shown as a try-chip.

## 2. Research basis

Bedrock v2 is assembled from proven systems rather than invented:

| Source | What we take | Why |
|---|---|---|
| **Radix Colors** 12-step scales | Every colour is a 12-step scale with fixed roles: 1–2 app bg, 3–5 component bg (rest/hover/pressed), 6–8 borders (subtle/interactive/strong+focus), 9–10 solid (rest/hover), 11–12 text (low/high contrast). | Tokens become roles, not taste. A new component never needs a new colour. Steps 11/12 are built to hit APCA Lc 60/90. |
| **Linear** LCH theme generation | The theme derives from 3 inputs (neutral hue, accent hue, contrast), and chrome tint is kept deliberately low. | Perceptually uniform space means equal L reads as equal lightness across hues. A high-contrast theme falls out for free. |
| **Material 3** dark surfaces | Elevation is expressed by lightness (higher surface = lighter), never pure black, and shadows only for overlays. | A shadow is invisible on a dark background, but a lightness step reads. |
| **APCA** (WCAG 3 candidate) | Contrast targets: body ≥ Lc 75, labels/meta ≥ Lc 60, large headings ≥ Lc 45, focus rings/icons ≥ Lc 45, placeholders ≥ Lc 30, dividers ≥ Lc 15. | WCAG 2 ratios over-rate light-on-dark text. For example, #B0B0B0 on #1E1E1E passes AAA but is only Lc 58. |
| **IBM Carbon** productive type | A dense "productive" scale for app UI (12/14/16/20/24/28/32/42) and fixed-size headings, plus a separate expressive set only for the home hero. | MC Utils is a lookup tool. Density and scanning beat drama. |

### Colour theory applied
- **Two neutrals, not one (simultaneous contrast).** A tinted surround shifts perceived hue toward its complement. A blue-grey panel makes a skin look warmer than it is. Chrome uses tinted **Deepslate**, while every surface that displays game art (tile art area, skin viewer, MOTD, cape render) uses achromatic **Stage**, so art is seen at true colour.
- **Hue harmony from the brand.** Amethyst (300°) is the brand. Its complement is 120°, and the split-complements at ±35° land on **Gold** (85°) and **Emerald** (155°). These become the highlight colour (rank, rare, top) and the success colour, so the status palette is harmonically related instead of arbitrary Tailwind reds and greens.
- **Materials as semantics.** Every hue is a Minecraft material players already read: Amethyst (brand/selection), Emerald (online/success), Gold (rank/highlight), Redstone (offline/danger), Copper (warning/degraded), Diamond (info/links-out). This gives game flavour through colour meaning, with no pixel fonts or fake game widgets.
- **60–30–10.** Deepslate surfaces fill about 60% of the area and text and borders about 30%. Amethyst plus materials stay at or under 10%, with at most one solid Amethyst element per view.
- **Helmholtz–Kohlrausch.** Saturated violet and red look brighter than their luminance says. Large fills never use step 9: big areas use steps 3–5 (low chroma) and step 9 is reserved for small controls. Accents are desaturated relative to the current `oklch(.7 .28 285)`, which over-glows on dark.
- **No pure black or white.** The page is Deepslate 1 (#101115), which avoids halation on OLED and leaves headroom for elevation. Text tops out at Deepslate 12 (#edeef1).

## 3. Principles

1. **Art at true colour.** Game content sits on achromatic Stage surfaces and is rendered `image-rendering: pixelated`. Nothing tinted touches it.
2. **Colour means something.** Every non-neutral colour is a material with one job. Decoration is never coloured.
3. **One family, two voices.** Everything is JetBrains Mono. Data (identifiers, numbers, commands) is bright and tabular, and labels are muted and medium weight.
4. **One skeleton.** Header band, then content grid, then footer, on one container. Entity pages share one layout.
5. **Every state is designed.** Loading, empty, not-found and error states each have human copy and a next action.

## 3a. Foundations

### Scales (dark, generated in OKLCH from Radix curves, sRGB-clamped)

| step | role | Deepslate (chrome) | Stage (art) | Amethyst | Emerald | Gold | Redstone | Copper | Diamond |
|---|---|---|---|---|---|---|---|---|---|
| 1 | app bg | `#101115` | `#111111` | `#16111e` | `#0e1510` | `#15120c` | `#191110` | `#17120f` | `#0a1718` |
| 2 | panel / subtle bg | `#18191d` | `#191919` | `#1b1525` | `#131a16` | `#1d180f` | `#201312` | `#1f1611` | `#0f1c1d` |
| 3 | component bg | `#212227` | `#222222` | `#2c1e42` | `#1a2a1f` | `#2d2207` | `#3b1311` | `#341d10` | `#042d31` |
| 4 | hover bg | `#282a30` | `#2a2a2a` | `#392358` | `#1a3a26` | `#3a2a00` | `#511011` | `#462008` | `#003a3f` |
| 5 | pressed / selected bg | `#2e3139` | `#313131` | `#432b65` | `#214830` | `#473300` | `#621717` | `#562809` | `#00474d` |
| 6 | subtle border | `#373a44` | `#3a3a3a` | `#4e3673` | `#27583a` | `#574001` | `#722423` | `#663516` | `#00565d` |
| 7 | interactive border | `#454853` | `#484848` | `#5e4387` | `#2f6845` | `#6b5216` | `#8c3431` | `#7d4524` | `#006a73` |
| 8 | strong border | `#5c6070` | `#606060` | `#7554a7` | `#347a50` | `#87691f` | `#b54642` | `#a15a2e` | `#00838e` |
| 9 | solid | `#696e7f` | `#6e6e6e` | **`#a167f1`** | `#2aa966` | `#fec63d` | `#e54948` | `#f07205` | `#00a5b3` |
| 10 | solid hover | `#767b8c` | `#7b7b7b` | `#ad74ff` | `#3db572` | `#ffd372` | `#ec5e59` | `#fc8231` | `#00b3c1` |
| 11 | low-contrast text | `#aeb3c3` | `#b4b4b4` | `#c3a4fb` | `#61d28f` | `#ffc949` | `#ff968e` | `#ff9f67` | `#46cedc` |
| 12 | high-contrast text | `#edeef1` | `#eeeeee` | `#e6dcfc` | `#b9f2cb` | `#ffe7b5` | `#ffd3ce` | `#ffdfcd` | `#b5edf3` |

Amethyst 9/10 are tuned up from the Radix curve (L .64/.68) so the solid works both as a button (white label Lc 69) and as a non-text indicator on bg (Lc 37). Steps 3–8 of a scale are its "tinted surface" set, used for badges, callouts and selected rows.

### Semantic tokens (what components use)

| Token | Value | Measured contrast |
|---|---|---|
| `--bg` | deepslate-1 | n/a |
| `--surface` | deepslate-2 | n/a |
| `--surface-raised` | deepslate-3 (popover, dialog, toast) | n/a |
| `--control` / `-hover` / `-active` | deepslate-3 / 4 / 5 | n/a |
| `--stage` | stage-2, plus a 6% radial lift to stage-3 behind art | n/a |
| `--border-subtle` / `--border` / `--border-strong` | deepslate-6 / 7 / 8 | divider Lc 15+ (≥ min for non-text) |
| `--text` | deepslate-12 | Lc 96 on surface |
| `--text-secondary` | `#ced1d9` (custom, L .86) for paragraphs | **Lc 77 on surface** (body ≥75 ✓) |
| `--text-muted` | deepslate-11 for labels and meta (≥13px/500) | Lc 60 ✓ |
| `--text-faint` | deepslate-10 for placeholder and disabled only | Lc 31 ✓ (≥30) |
| `--accent` / `-hover` | amethyst-9 / 10 | white label Lc 69 |
| `--accent-surface` | amethyst-3 (selected row/segment), with amethyst-7 border | n/a |
| `--accent-text` | amethyst-11 (links, active nav) | Lc 60 on surface |
| `--focus` | amethyst-11 ring, 2px, offset 2px | Lc 61 vs bg ✓ (≥45) |
| `--success` / `--highlight` / `--danger` / `--warning` / `--info` | emerald / gold / redstone / copper / diamond. Use 9 for dots and icons, 11 for text, 3+7 for tinted callouts | text Lc 60–77 |

Rule: text on a gold-9 or emerald-9 solid is dark (`#111113`). Text on amethyst, redstone, copper and diamond solids is white. This matches the Radix guidance that bright hues expect dark text.

### Typography
**JetBrains Mono** stays the single family; it is the site's identity. It is loaded as the variable font with its full `100 800` range, so weights 500–700 are real instead of faux-bold. With one family, data and prose are separated by colour and weight instead of typeface: data is `--text`/400 with `tabular-nums`, and labels are `--text-muted`/500.

Productive scale (Carbon steps). Headings stay fixed-size on mobile, except the home hero, which uses the expressive scale.

| Role | Family | Size / line | Weight | Tracking |
|---|---|---|---|---|
| hero (home only, expressive) | mono | 42/48 → 32/38 mobile | 650 | -0.03em |
| h1 | mono | 28/36 | 650 | -0.02em |
| h2 | mono | 20/28 | 600 | -0.01em |
| panel title | mono | 14/20 | 600 | 0 |
| body | mono | 16/24 (prose), 14/20 (UI) | 400 | 0 |
| label / meta | mono | 13/18 | 500 | 0 |
| data | mono | 13/20 | 400 | `tabular-nums` |
| stat value | mono | 20/28 | 500 | `tabular-nums` |

Title case for page and section titles (h1, h2, entity names). Sentence case for everything else: panel headers, labels, buttons, state messages and body. No uppercase letter-spaced micro-labels. Prose max width is 65ch.

### Space, radius, elevation, motion
- **Grid:** 4px base on Tailwind's default scale; delete the custom `--spacing-*` tokens. Panel padding is 16px. Gaps are 12px in a panel, 16px between tiles and 32px between sections. One container: `max-w-[1200px] px-4 md:px-6`.
- **Radius:** one radius, 6px (`--radius`), on every control, chip, panel, tile and overlay; every `rounded-*` token resolves to it. Full rounding only for status dots. No joined groups with square inner corners: separate controls with a gap instead.
- **Elevation (M3 rule):** raise by lightness (bg → surface → surface-raised). Each surface also gets a 1px `inset 0 1px 0 oklch(1 0 0 / .04)` top highlight, the Linear-style lit edge. Shadows (`0 12px 32px oklch(0 0 0 / .55)`) go on overlays only.
- **State layers:** hover and pressed move one scale step (3→4→5), which is more predictable on dark than M3's 8/12% opacity overlays.
- **Motion:** 120ms ease-out for hover and press, and 180ms for overlays. Tiles lift 2px on hover. Skeletons use a static control colour with a 1.6s opacity pulse. All of it is disabled under `prefers-reduced-motion`.

### Iconography and imagery
- lucide-react only: 16px in controls and 20px in states, stroke 1.75, `currentColor`. Remove the unused icon and chart packages.
- **Header band:** the page's themed screenshot appears only behind the page header (about 280px). It is blurred 8px, at 35% opacity and masked into `--bg`, and every route gets one.
- Missing render: a Stage-3 silhouette with "No preview", never an empty box. Avatars always have alt text and sit on Stage-3 so dark skins don't vanish.

## 4. Components

Every component lives in `src/components/ui` and is built on Radix (`radix-ui` is already a dependency) where there is a behavioural primitive.

- **PageShell:** container, header band, `<main id="content">` and footer. Variants: `page` (title + description + actions) and `entity` (see below).
- **PageHeader:** optional back-link breadcrumb (`← Skins`), h1, one-line description in `--text-muted`, and right-aligned actions.
- **EntityHeader:** media (head render, server icon, cape) at 64px (mobile 56px), then h1, then an identifier row of mono **IdChips** with copy, then a **StatRow** of 2–4 key facts. Used by player, server, skin, cape and IP pages.
- **Panel:** replaces Card+CardHeader and the 8 hand-rolled micro-headings. The header (44px, `--control`, bottom `--border-subtle`) holds the title, an optional count badge and optional actions (filter input, copy, view-all). The body is `p-4` or flush for lists and tables.
- **KeyValueList:** replaces ProfileFields. Two columns (label `--text-muted` sans 13px, value `--text` mono 13px), a row copy button that only appears on row hover/focus and is always visible on touch, optional group subheadings, and an info tooltip for jargon.
- **DataTable:** adopt `ui/table` (DNS records, name history). It has sticky header, zebra-free rows, row dividers and horizontal scroll on mobile.
- **IdChip:** mono value plus copy icon. The whole chip is the button and is labelled `Copy UUID`. On copy, the icon morphs to a check for 1.2s and a toast fires.
- **Tile (media grid card):** replaces MediaCard and the duplicated skin/cape previews. A 3:4 art area on `--surface`, then a title (sans 14/600, truncate) and a meta line (`👤 12,345 owners` as an icon plus mono tabular number). On hover or focus it lifts with a `--border-strong` border and a 2px `--focus` outline on focus-visible. It always has a fallback art state.
- **MediaGrid:** responsive columns of 2 (xs), 3 (sm), 4 (md) and 5 (lg and up; API pages hold 25 items, so 5 columns never orphan a tile) at a 16px gap, with a skeleton of the same count.
- **SegmentedControl:** sort tabs (Trending / Top / Latest). A Radix ToggleGroup rendered as links; the selected item is `--accent-surface` with `--text` text, not solid purple.
- **Pagination:** prev, numbered pages and next, 40px squares; the current page uses the outlined-primary style. Shows "Page 2 of 14" on mobile instead of numbers.
- **Search (combobox):** a proper ARIA combobox (`role=combobox` plus `listbox`/`option`, `aria-activedescendant`). Arrow keys and Enter select the highlighted suggestion, and Enter with nothing highlighted runs a typed lookup. Groups are Recent, Players and Servers, with type detection shown inline ("Looks like a server address"). Inputs validate client-side with human copy ("That's not a valid username, UUID or server address"). The `/` shortcut focuses search from anywhere. The hero variant is 48px and the navbar variant 40px. On mobile, search opens a full-screen sheet (current behaviour, kept).
- **Button:** `primary` (filled, at most one per view), `secondary` (`--control` + `--border-subtle`), `ghost` and `icon` (always has an `aria-label` and tooltip). Sizes are sm 32, md 40 and lg 48.
- **Tooltip:** Radix Tooltip replaces the custom one. It is not a tab stop of its own; it attaches to the trigger. On touch, it becomes a tap-to-toggle popover.
- **Toast:** Sonner themed with tokens (`--control`, `--border-strong`, sans 14px), bottom-center on mobile and bottom-right on desktop.
- **State blocks:** one `StateBlock` component with variants `empty`, `not-found`, `error` and `loading`. It has a 20px icon, a title, one line of body and an action. Titles are `--text`, never red; only the icon is tinted by `--danger`.
  - Player not found: "No player called **zzzz_not_real_zz**". Shows the search input prefilled and a "Similar names" list when the API offers one.
  - Server unreachable: "**this.does.not.exist.example** didn't respond". Shows the reason mapped from the error code (DNS failure, timeout, blocked) and a Retry button.
  - 404: "This page doesn't exist" with search and links to Skins, Capes and Names.

## 5. Page patterns

- **Home:** the header band holds the display title, a 48px search and try-chips. Below that sits a stat row (two stats as mono data-lg plus a sans label), then the tool tiles (Capes, Skins, Names, **IP lookup**) as a 4-up row (2×2 on mobile) with icon, title and one line each. Then come Trending skins (MediaGrid, 2 rows, "View all") and the about text in sans body at a max width of 65ch.
- **Player:** EntityHeader (head, name, UUID chip, stats: first seen, skins count, capes count). The grid is a 360px aside with the skin viewer (2D/3D toggle and pose SegmentedControl with labelled buttons; download as a secondary button) next to a fluid main column with Name history (DataTable: name, changed at, copy), Skins (Tile grid, small) and Capes. Below lg, everything stacks with the viewer first.
- **Server:** EntityHeader (icon, name, hostname/IP chips, players online / version). The MOTD banner goes full-width as the hero visual, followed by a Details KeyValueList (grouped: Connection, Chat) and DNS records as a DataTable.
- **Skin / Cape detail:** EntityHeader (render, name, owners count, first recorded) with the viewer in the aside and the head command in an aside panel directly under the viewer. Main holds an Owners panel: server-side filtered, paginated 20 at a time, rendered as a compact list (head, name, "since"), never 700 inline chips.
- **Skins / Capes index:** PageHeader, SegmentedControl, MediaGrid, Pagination.
- **Names:** the original compact live list (head, old → new, relative time) in a single panel; it reads better than a table.
- **IP lookup:** the same header band and PageHeader, with the lookup input in the band. The result is an EntityHeader (IP, version badge, network) and a KeyValueList (Organization, ASN, CIDR, location) plus the map. The duplicate summary tile is removed, and the "Your IP" chip is hidden when the IP is loopback.

## 6. Navigation and footer

- **Desktop navbar** (56px, `--bg` at 80% with backdrop blur, bottom `--border-subtle`): logo (mark plus "MC Utils" in `--text`), then labelled links (Skins, Capes, Names, IP lookup). The active link gets `--text` text and a 2px `--accent` underline, with no filled pill. Search with a `/` hint sits on the right.
- **Mobile navbar:** logo, search button and menu button. The menu opens a Radix Dialog sheet with labelled links and the external resources.
- **Footer:** shares the container. It has a brand line, Tools (all 4 tools) and Resources columns with headings in `--text` 14/600, plus a bottom row with the API status dot and source link.

## 7. Content and voice

- Plain, short and specific: "Copied UUID" rather than "Copied to your clipboard".
- Error copy names the thing and the fix, and never echoes stack or API text. All API error codes are mapped in a single `errorMessage(code)` helper.
- Counts use full localised numbers in tables and compact numbers (65.9M) in stat rows, with the full number in the tooltip.
- Times are relative ("2m ago") with an absolute `title`/tooltip.

## 8. Accessibility checklist (required for every component)

- A visible `focus-visible` ring: 2px `--focus`, 2px offset, on every interactive element including tiles.
- Every icon-only control has an `aria-label` and a tooltip with the same text.
- Every image has `alt`; decorative backgrounds use `alt=""`/`aria-hidden`.
- Touch targets are ≥44px. Inputs are ≥16px on mobile.
- Text contrast is ≥4.5:1 (≥3:1 for ≥18px), and status is never conveyed by colour alone.
- One breakpoint source: `useIsMobile` reads the same `md` value Tailwind uses (`min-width: 768px` means desktop).
