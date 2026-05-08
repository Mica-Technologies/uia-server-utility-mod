# Migrating from EconomyInc to SUM-native economy

SUM ships its own currency capability + bill items + ATM + bank kit + player
shop + bill changer + bills display block + chest loot inject. When
EconomyInc is loaded, SUM defers to EconomyInc's `IMoney` capability for
balance storage; when EconomyInc is removed, SUM's `ISumMoney` capability
takes over.

This doc describes how to retire EconomyInc on a server that already has it
in production and switch the economy over to SUM.

## What gets migrated

The `/sum migrate-economy` command (op-only) migrates **online players** in
two pieces:

1. **Account balance.** Reads each online player's EconomyInc `IMoney`
   balance, zeroes it, and credits the equivalent amount to that player's
   SUM `ISumMoney` capability.
2. **Bill items.** Replaces every `economy:item_*` bill item in the
   player's main inventory + offhand + hotbar with the equivalent
   `sum:bill_*` of the same denomination and stack count. EconomyInc's
   typo-named items (`item_fiftybe`, `item_hundreedb`, etc.) are handled.

What's **not** migrated:
- **Offline players**: only currently-online players are processed. Have
  everyone log in before running the command, or run the command again
  when stragglers come online.
- **Items in containers** (chests, shulker boxes, ender chests stored on
  disk): not scanned. Players will need to pull bills out of their own
  chests and into their inventory before the migration so the in-inventory
  pass picks them up. Alternatively, deposit them at an ATM first — SUM's
  ATM accepts both EconomyInc and SUM bills for deposits.
- **In-world EconomyInc blocks** (ATMs, vaults, sellers, plots, etc.):
  not converted. These will become "missing block" stubs once EconomyInc
  is uninstalled. Plan to remove them with WorldEdit before the EconomyInc
  pull, or accept the loss and let players replace with SUM equivalents.
- **Plot ownership data**: SUM has no plots system (Section D research
  only). EconomyInc's `data/plots.dat` becomes orphan data after removal;
  delete it manually.

## Recommended procedure

1. **Communicate the migration window.** Tell players to log in during
   the window. Anyone who skips the window keeps their EconomyInc balance
   on their player file but loses access to it once EconomyInc is removed
   — they'll have to be manually reimbursed via `/sum econ add`.
2. **Pre-flight: dry-run.** Run `/sum migrate-economy verify`. Check the
   per-player report and the total. If totals look wrong, investigate
   before committing.
3. **Run the migration.** `/sum migrate-economy`. The command prints the
   per-player conversions and a final total.
4. **Stop the server.**
5. **Remove EconomyInc** from the modpack (delete the jar from `mods/`).
6. **Remove the EconomyInc config** if you don't need it (`config/economy*`).
7. **Optional: remove orphan data.** `world/data/plots.dat`,
   `world/data/chunks.dat`, etc. (Forge will recreate the few that SUM
   uses on next start.)
8. **Restart the server.** SUM's `ISumMoney` capability is now the active
   backend. `/balance` reports the migrated balance. ATMs produce SUM
   bills.

## Re-running migration

The command is safe to re-run: every successful pass zeroes the source
EconomyInc balance, so a second run finds nothing to move. Bill conversion
is also idempotent — once a stack is `sum:bill_X`, the converter ignores
it (it only matches items in the `economy:` namespace).

## Manual top-up for missed offline players

If a player misses the migration window and you want to compensate them
once they finally log in:

```
/sum econ add <player> <amount>
```

This works whether SUM or EconomyInc is the active backend (the bridge
routes appropriately), so it's safe to use both before and after the
removal.
