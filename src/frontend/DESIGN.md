# Munehisa — Design System (**Sumi** skin, primary theme)

> **Thesis.** Munehisa is an investment cockpit that travels through time. It's not generic fintech: it's a **~1750s Japanese brokerage artifact reimagined as an instrument** — Japanese minimalism (間 negative space, restraint, ink) with clear software affordance. Reductionist with attitude: few elements, each one loaded with intent.
>
> A tribute to **Munehisa Homma**, the Osaka rice merchant who invented the candlestick. Visual heritage: **washi paper, sumi ink, vermilion seal, kanji**.
>
> Golden rule when creating any new screen: **number = reading (no frame); button = chrome (outline + shadow)**. If you can't tell what's clickable just by looking, it's wrong.

---

## 1. Colors

Sumi is **light** (daytime paper). One warm accent only. No decorative gradients, no diffuse colored shadows.

| Token | Hex | Use |
|---|---|---|
| `--bg` (paper) | `#EFE8DA` | Screen background / washi surface |
| `--panel` | `#E7DECE` | Panels and cards (one shade off the paper, gives structure without a heavy border) |
| `--ink` (sumi) | `#1B1611` | Main text, titles, ink |
| `--name` | `#3A332A` | Secondary text / names in tables |
| `--muted` | `#7A705F` | Labels, captions, metadata, hairlines in text |
| **`--vermilion` 朱** | **`#DD3A22`** | **The only warm tone.** Primary action, "the now", **RISE**, seal icons |
| `--teal` (2nd accent) | `#0C6156` | Secondary action, **FALL**, links, ↗ arrows |
| `--onVerm` | `#EFE8DA` | Text/icon over vermilion |

**Borders & rules (on paper):** derived from the ink with alpha — don't invent new grays.
- Panel border: `#1B16111f` · Highlight card border: `#DD3A2230`
- Table header: `#1B161130` · Table row (hairline): `#1B161114`
- Ink-button border: `#1B161140` · Chip border: `#1B161130`

**Donut / category palette** (fixed order): `#DD3A22` · `#1B1611` · `#B08A3E` · `#6E7A5E` · `#A79A86`.

### Semantic color rule — NON-NEGOTIABLE
- **Vermilion `#DD3A22` = RISE / gain / execute / "now".** (Osaka 1750 style: red is a rise, not a fall.)
- **Teal `#0C6156` = FALL / secondary / link.**
- Never use green for "went up" or red for "went down." That is the brand's signature.
- One warm accent per screen. If everything is red, nothing is.

---

## 2. Typography

Three families, three roles. Load via Google Fonts.

```html
<link href="https://fonts.googleapis.com/css2?family=Zen+Old+Mincho:wght@400;700;900&family=Zen+Kaku+Gothic+New:wght@400;500;700&family=Space+Mono:wght@400;700&display=swap" rel="stylesheet">
```

| Family | Role | Where |
|---|---|---|
| **Zen Old Mincho** (serif, 700/900) | **The ink.** Display, section titles, ALL large monetary numbers, kanji glyphs | `MineInvest`, `R$ 722,000.00`, `FEB 2002`, "Current positions" titles |
| **Zen Kaku Gothic New** (sans, 400/500) | **The interface.** Running text, names, descriptions | asset names, supporting text |
| **Space Mono** (mono, 400/700) | **The data.** Technical readouts, tracked labels, tickers, %, ISO dates, buttons | `MXRF11.SA`, `+1.11%`, `2002.02`, `CASH BALANCE` labels |

**Rules:**
- Large monetary values → **Zen Old Mincho 700**, ~31px in a card, ~26px+ for date highlights.
- Labels → **Space Mono**, 9–11px, `letter-spacing: .14em–.2em`, `--muted` color, generally UPPERCASE.
- Ticker, quantity, price, %, ISO date → **Space Mono**.
- Decorative/icon kanji → **Zen Old Mincho** (never emoji).
- **No Inter/Roboto/Arial.**

---

## 3. Texture & surface (what makes it "Sumi," not SaaS)

Over the paper, a **washi grain** layer in `mix-blend-mode: multiply`. Apply on the screen container, `position:absolute; inset:0; pointer-events:none`:

```css
mix-blend-mode: multiply; opacity: .45;
background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.26'/%3E%3C/svg%3E");
```

- No rounded corners on main containers — **square corners** (it's paper/an instrument, not a bubble). Radius 0 is the default.
- Outer shadow only as a thin instrument frame: `box-shadow: 0 0 0 3px #211E18` (a "display case" border). Never a soft, diffuse Material-style shadow.

---

## 4. Components

### Buttons (affordance is law)
Every button has a border and/or solid fill + a "stamp" micro-shadow. Never loose clickable text.

- **Primary (vermilion):** `background:#DD3A22; color:#EFE8DA; border:none; box-shadow:0 2px 0 #9E2413;` hover `#C7331D`. E.g., `▸▸ Advance month`.
- **Strong secondary (teal):** `background:#EFE8DA; color:#0C6156; border:1px solid #0C6156; box-shadow:0 2px 0 #0C615622;`. E.g., `⇄ Trade assets`.
- **Ink / neutral:** `background:#EFE8DA; color:#1B1611; border:1px solid #1B161140; box-shadow:0 1px 0 #1B161114;` hover `#1B16110c`. E.g., `← Back`, `⟲ Reset`.
- **Small chip:** `border:1px solid #1B161130; padding:6px 12px;` Space Mono 11px. E.g., `+ Contribute`, `Show ▾`.
- Button font: **Space Mono**, 11–12px, `letter-spacing:.1em` on primaries.

### Stat cards
`background:--panel; border:1px solid #1B16111f; padding:18px 20px;` square corners.
- Header = **kanji seal icon** (36px square, `--ink` or `#DD3A22` background, glyph in Zen Old Mincho) + Space Mono `--muted` label.
- Value = Zen Old Mincho 700 ~31px, `--ink` (or `#DD3A22` if positive P&L).
- P&L card uses `#DD3A2230` border for a subtle highlight.

### Icons = kanji seals (not pictograms)
A solid square with a white kanji in Zen Old Mincho. Fixed vocabulary:
`金` cash/money · `株` stocks/portfolio · `総` total · `利` gain/profit · `札` history/note · `進` advance. `--ink` background for neutral, `#DD3A22` for value/action.

### Table (positions)
- Header: Space Mono 10px, `.08em` tracking, `--muted`, bottom border `#1B161130`.
- Rows: `#1B161114` hairline; ticker in Space Mono 700; name in Zen Kaku 14px `--name` (+ teal ↗ arrow); numbers right-aligned in Space Mono; P&L colored per the rule (red = rise, teal = fall).

### Allocation donut
`conic-gradient` using the category palette in fixed order; core (`inset:36px`) in `--panel` color with the count in Zen Old Mincho + Space Mono label.

---

## 5. Layout & spacing

- Wide instrument screen: container ~1360px, `padding:26px 30px 32px`.
- Stat card grid: `repeat(4,1fr)`, `gap:16px`.
- Body: flex row `gap:16px` — table `flex:1.75`, allocation `flex:1`.
- **Use flex/grid with `gap`** — never loose margins or whitespace-based spacing.
- Respect 間 (ma): controlled density, air between blocks. Don't fill space with useless data.

---

## 6. Core mechanic — time

- The simulation begins on a start date (e.g., **Feb 2002**) and **advances month by month** to the current month, via a button (`▸▸ Advance month`, vermilion primary).
- **Current date** is always visible and highlighted: a bordered box, `CURRENT DATE` label (Space Mono) + month/year in Zen Old Mincho.
- Also show the **Japanese era** when it makes sense: 平成 (Heisei, through Apr 2019) / 令和 (Reiwa, May 2019+). Format: `令和7年 2月`.
- Short ISO format for readouts: `2002.02`.
- Dates in en-US; abbreviated months UPPERCASE: `JAN FEB MAR … DEC`.

---

## 7. Dark mode (**Zankyō** skin — secondary)

The same instrument at night. Only the temperature changes; **structure, typography, layout, and color semantics are identical**. Main overrides:

| Token | Sumi (light) | Zankyō (dark) |
|---|---|---|
| `--bg` | `#EFE8DA` | `#14100C` |
| `--panel` | `#E7DECE` | `#1B150F` |
| `--ink` / `--title` | `#1B1611` / `#1B1611` | `#EDE4D3` / `#F1E8D7` |
| `--muted` | `#7A705F` | `#8E8474` |
| 2nd accent | teal `#0C6156` | phosphor cyan `#57E3D0` |
| texture | washi grain `multiply` op .45 | grain `soft-light` op .5 + cyan scanline |
| button shadow | solid `0 2px 0` | contained `0 0 Npx` glow |

Vermilion `#DD3A22` stays the same in both (rise/action). Dark-mode donut palette: `#57E3D0 · #DD3A22 · #F0B84E · #9B7BE0 · #EDE4D3`.

---

## 8. Don'ts

- ❌ Conventional stock-market green/red (here, red = rise).
- ❌ Large rounded corners, soft diffuse shadows, decorative gradients.
- ❌ Inter/Roboto/Arial; emoji as icons.
- ❌ Clickable text without button chrome.
- ❌ Decorative data/stats ("data slop"). Every number has a function.
- ❌ More than one warm accent per screen.
- ❌ Generic line pictograms instead of kanji seals.