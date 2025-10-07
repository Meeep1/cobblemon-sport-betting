## Cobblemon Betting Mod (Server-Side)

Bet on Cobblemon battles using in-game item currency. Auto-opens in your arena, shows odds and live remaining Pokémon on a bossbar, pays winners with fireworks, and supports props/parlays and simple tournaments. Fully server-side with Fabric API.

• Minecraft 1.21.1 • Fabric Loader/API • Cobblemon 1.6.1 (optional, auto-hooked if present) • Java 21

---

## Features

- Betting on head-to-head matches with admin-settable odds (A vs B)
- Numismatic Overhaul coins as currency (bronze/silver/gold) with tag fallback
- Chest-style GUIs for betting and props/parlays
- Arena auto-open using regions (Area A / Area B / Arena)
- Bossbar lifecycle:
	- Odds display while betting is open
	- Live “A vs B” with remaining Pokémon during battles
	- Winner rainbow bossbar with optional fireworks
- Automatic payout resolution with House Pays or pooled-scaling modes
- Result slips on resolution: WON / LOST / REFUND (with scaled percentage note)
- Props & Parlays: First Faint A/B; Total Faints > threshold; configurable multipliers
- Gated /openprops while betting is open
- Sound cues and self-bet restriction feature flags
- NPC trainer battle support (PvE) inside the arena
- Simple tournaments: create, add entrants, start, and auto-advance on battle wins

---

## Installation

1) Build or download the JAR
- Build locally (see Build) or use a released JAR.

2) Server mods folder
- Place the JAR in your server's `mods/` folder alongside Fabric API and (optionally) Cobblemon and Numismatic Overhaul.

3) Start the server
- The mod creates/config updates on first run.

---

## Currency

Accepted items:
- Tag: `my_betting_mod:currency` (datapack-editable)
- Defaults if Numismatic Overhaul is present:
	- `numismatic-overhaul:bronze_coin` (1 bronze)
	- `numismatic-overhaul:silver_coin` (100 bronze)
	- `numismatic-overhaul:gold_coin` (10,000 bronze)

Accounting and payouts are done in bronze units and paid out with highest denominations first.

---

## GUIs

### Betting Menu (`/openbetmenu`)

- A (emerald) and B (redstone) buttons show names and odds.
- Center stake slot accepts only currency items.
- Confirm (gold ingot): reads your stake in the slot, converts to bronze, and places a bet.
- Take Back Bet: refunds your confirmed bets while betting is open.
- Behavior mirrors prepaid flow: the slot isn’t cleared until you confirm successfully.

### Props/Parlay Menu (`/openprops` while betting is open)

- Add 1–2 legs:
	- First Faint A or B (multiplier from config)
	- Total Faints > threshold (threshold and multiplier from config)
- Stake slot accepts only currency; coins remain visible until you confirm successfully.
- Place Parlay: on success, creates a slip and sound cue (if enabled).

---

## Bossbar & Arena Lifecycle

- Auto-open when exactly one player is in Area A and one player is in Area B (same dimension), unless in cooldown.
- Betting odds are displayed on a bossbar to nearby players.
- At battle start, betting closes and bossbar switches to live mode with remaining Pokémon counts.
- At battle end, a winner bossbar appears (rainbow); optional fireworks can be launched at a configured position.
- A small cooldown prevents immediate re-opening after a match.

---

## Cobblemon Integration

- Uses defensive reflection to subscribe to Cobblemon events:
	- BATTLE_STARTED_POST → close betting and show live bossbar.
	- BATTLE_FAINTED → update remaining counts.
	- BATTLE_VICTORY / BATTLE_FLED → settle bets, show winner, fireworks, and clear live state.
- NPC trainer battles are supported (player vs trainer actor) when inside the arena.

---

## Odds, Payouts, and Slips

- Odds are integers representing x100 multipliers (e.g., 150 = 1.5x).
- House Pays ON: winners are paid full odds; losers lose stake.
- House Pays OFF: winners are paid from the loser pool; if insufficient, payouts scale down with a percentage shown on slips. If no opposing bets exist, winners are refunded.
- Result slips on resolution (when receipts are enabled):
	- WON: shows stake and payout, glint enabled; includes scaled % when applicable
	- LOST: shows target and lost stake
	- REFUND: shows refund amount when no opposing bets existed
- Parlay slips on resolution:
	- Win: combined multiplier, stake, leg details, glint
	- Loss: failed leg noted, no glint

---

## Regions & Fireworks

Define three regions (Axis-aligned boxes in a dimension):
- Area A: waiting area for Player A
- Area B: waiting area for Player B
- Arena: actual battle area (also used to gate Cobblemon event reactions)

Fireworks (optional): set a dimension and BlockPos to launch a few bursts on winner announcement.

Regions and fireworks are stored in the mod’s JSON config and editable in-game via the Admin Menu.

---

## Commands

Player:
- `/openbetmenu` — open betting menu (when betting is open)
- `/openprops` — open props/parlay menu (gated to betting open)
- `/bethelp [props|admin]` — in-game help

Admin:
- `/setodds <playerA> <oddsA> <playerB> <oddsB>` — set odds (x100)
- `/odds add|remove|list` — manage predefined odds for pairs
- `/openadminmenu` — toggle House Pays, Auto-Open, regions, fireworks
- `/betflags receipts|sounds|self on|off` — feature toggles
- `/betprops show|set firstfaintmult|totaloverthr|totalovermult` — props configuration
- `/betdebug menu|reset|resolve <A|B>` — testing and manual resolution helpers

Tournaments:
- `/tourney create <id>` — create a fresh bracket
- `/tourney add <name…>` — add an entrant by name (supports spaces)
- `/tourney addplayer <player>` — add an entrant by online player profile
- `/tourney remove <name…>` — remove entrant by name (before start)
- `/tourney removeplayer <player>` — remove entrant by profile
- `/tourney list` — list current entrants and state
- `/tourney start` — build the bracket and announce the first match
- `/tourney status` — broadcast current or next match/champion

---

## Configuration

Feature Flags (`/betflags`):
- Receipts — give bet/parlay placement slips and result slips
- Sounds — play UI click sounds on confirms
- Self — disallow betting on your own match

Props (`/betprops`):
- First Faint multiplier (x100)
- Total Faints Over threshold
- Total Faints Over multiplier (x100)

Predefined Odds (`/odds`):
- Save commonly used A/B odds and auto-apply on auto-open.

House Pays:
- When enabled, full advertised payouts are covered by the house.

Cool-down and Winner delay:
- Short cool-down prevents instant re-open; winner bossbar has a display window before odds reappear.

---

## Build

Standard Fabric Loom project.
- JDK 21
- Minecraft 1.21.1

Build locally:

```powershell
./gradlew.bat build
```

The JAR will be in `build/libs/`.

---

## Troubleshooting

- “Betting is closed” when using `/openbetmenu`:
	- Ensure auto-open is enabled and one player is in Area A and one in Area B, or set odds manually with `/setodds` to open.
- Coins not accepted in GUI:
	- Ensure you’re using coin items or that your custom items are in the `my_betting_mod:currency` tag.
- No result slips or sounds:
	- Enable via `/betflags receipts on` and `/betflags sounds on`.
- Tournament add failures:
	- Use `/tourney add <name…>` for multi-word names or `/tourney addplayer <player>` to add a current player.
- Props menu stake disappearing:
	- The stake slot is preserved; coins are only consumed on successful confirm.

---

## License

This project inherits its license from the original template. See `LICENSE`.
