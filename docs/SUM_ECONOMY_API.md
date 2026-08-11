# SUM Economy API

**For mod developers.** How to move a player's money from your own mod, on any
server running SUM.

API version 1. Requires SUM (`sum`) on both the compile and runtime classpath.

---

## Not the Open MCEconomic API

Two things in this repository have similar names. They solve unrelated problems:

| | What it is | Who it's for |
|---|---|---|
| **SUM Economy API** — this document | A Java API for other **mods** to read and move player money | Mod developers |
| **Open MCEconomic API** — `docs/OPEN_MCECONOMIC_API_SPECIFICATION.md` | An HTTP protocol SUM speaks to an external economy **service** | Server operators running a shared economy backend |

A server may run either, both, or neither. **This API behaves identically
whichever is the case** — that is most of the point of it. You never need to
know where a server keeps its money.

---

## The three pools of money

Confusing these is the most common way to write a broken integration, so start
here.

| | What it is | Where it lives | How you use it |
|---|---|---|---|
| **Wallet** | What a player carries, and what every in-game purchase spends. An invisible balance **plus** the face value of bill items in their inventory. | Always the world save | Synchronous. Cannot fail after the fact. |
| **Bank** | Savings, normally reachable only at an ATM. | A remote service if the server configures one, otherwise the world save | Asynchronous, callback. Can be refused after a round trip. |
| **Escrow** | Wallet money the server holds for you between a commitment and its resolution. | The world save | Synchronous. Survives a restart. |

**If you are unsure, you want the wallet.** A casino takes wagers from the
wallet. A shop charges the wallet. The bank is savings a player has deliberately
put away, and reaching into it from a mod is closer to a debit card than to
handing over cash.

---

## Quick start

### 1. Depend on SUM

Compile against the slim `-api` jar published on every SUM release
(`uia-server-utility-mod-<version>-api.jar`), and depend on the full mod at
runtime.

```gradle
dependencies {
    compileOnly files('libs/uia-server-utility-mod-2026.08.09-api.jar')
}
```

Declare a hard dependency so your mod is never loaded into a world with no
economy to talk to:

```java
@Mod(modid = "mycasino", dependencies = "required-after:sum")
```

> Do not pin to a **pre-release** api jar. Pre-releases are deleted after 90
> days, which will break your build later.

### 2. Get a handle

```java
import com.micatechnologies.minecraft.sum.api.*;

private EconomyHandle economy;

@Mod.EventHandler
public void serverStarting(FMLServerStartingEvent event) {
    Optional<EconomyHandle> maybe = SumEconomy.acquire("mycasino");
    if (!maybe.isPresent()) {
        LOGGER.warn("No SUM economy: {}", SumEconomy.describeDenial("mycasino"));
        return;   // disable your money features, don't crash
    }
    economy = maybe.get();
}
```

**Acquire during or after `FMLServerStartingEvent`.** The economy backend is
chosen when the server starts, so asking in `preInit` is asking too early and
always returns empty.

### 3. Get the operator to authorize you

`acquire` returns empty until a server operator adds your mod to SUM's config:

```
economy_integration {
    S:allowedMods <
        mycasino=escrow
     >
}
```

Then `/sum econ api reload`, or a restart. A denial is logged with the exact
line to add, so tell operators to read their server log.

---

## Authorization and scopes

A mod may only do what its scopes allow. Anything else fails with
`MISSING_SCOPE` and moves no money.

| Scope | Lets you |
|---|---|
| `wallet_read` | Read a wallet balance, test affordability |
| `wallet_write` | Spend from and credit to a wallet. Implies `wallet_read` |
| `bank_read` | Read a bank balance |
| `bank_write` | Deposit to and withdraw from a bank. Implies `bank_read` |
| `escrow` | Hold wallet money. Implies `wallet_write` |
| `*` | Everything |

Ask for the least you need. A gambling mod wants `escrow` and nothing else —
that already carries the wallet scopes, and staying off `bank_*` means an
operator can see at a glance that you cannot reach anyone's savings.

Check at startup if you would rather disable a feature than fail per-use:

```java
if (!economy.hasScope(EconomyScope.ESCROW)) {
    LOGGER.warn("mycasino is authorized but not for escrow; wagering is off.");
}
```

### What the allowlist actually is

**It is an operator control and an audit trail. It is not a security boundary.**

It gives an operator three real things: they can revoke one integration without
a code change, a mod that forgot to ask fails loudly instead of half-working,
and every transaction is tagged with the mod that made it.

It cannot stop a mod that is determined to reach into SUM's internals directly.
Nothing running inside the same JVM could. Mod ids are self-declared and taken
at face value. Do not describe this to operators as a sandbox, and do not rely
on it to protect you from another mod.

---

## Ground rules

**Server side only.** Every mutation must run on the server thread. Calls from
the wrong thread are refused with `WRONG_THREAD` rather than allowed to corrupt
state — the wallet mutates player inventory and capabilities with no locking, so
an off-thread call is a genuine data race. Calls from a client are refused with
`CLIENT_SIDE`.

**A failed result means nothing moved.** Every operation is all-or-nothing. If
`isOk()` is false, no wallet, bank account or hold was changed, and you have no
compensating action to take.

**Balances are `OptionalDouble`, never `NaN`.** SUM uses `NaN` internally for
"unknown", and `NaN >= amount` is silently false in every comparison you would
naturally write. The API never exposes it.

**Bank callbacks fire exactly once, on the server thread** — including on
immediate rejection. It is safe to touch world state inside one.

---

## Reference

Every method is on `EconomyHandle`.

### Status

```java
EconomyStatus getStatus();
```

`isAvailable()`, `isRemoteBank()`, `isBankDegraded()`, `getCurrencySymbol()`,
`getBankMinorUnitDigits()`. Re-read it rather than caching — a remote bank can
go degraded and recover within a session.

### Wallet

| Method | Scope | Notes |
|---|---|---|
| `OptionalDouble getWalletBalance(player)` | `wallet_read` | Invisible balance plus carried bills |
| `boolean canAfford(player, amount)` | `wallet_read` | A hint, not a reservation |
| `EconomyResult walletSpend(player, amount, reason)` | `wallet_write` | Breaks carried bills if needed |
| `EconomyResult walletCredit(player, amount, reason)` | `wallet_write` | **Creates money** — see below |

Amounts are rounded to whole cents (half-up) before anything moves.

`walletCredit` is not paired with a debit anywhere: it mints money. Use it for
winnings, rewards and refunds, and expect operators to cap it with
`maxWalletTransaction`.

`canAfford` is racy by nature — nothing stops the player spending between your
check and your charge. Prefer calling `walletSpend` and handling
`INSUFFICIENT_FUNDS`, or `escrowOpen` when you need the money actually held.

### Bank

| Method | Scope | Notes |
|---|---|---|
| `OptionalDouble getBankBalance(player)` | `bank_read` | Never blocks; empty is normal just after login on a remote backend |
| `double quantiseForBank(amount)` | — | Rounds **down** to what the bank can hold |
| `void bankDeposit(player, amount, reason, callback)` | `bank_write` | Does **not** take it from the wallet |
| `void bankWithdraw(player, amount, reason, callback)` | `bank_write` | Does **not** put it anywhere |

Bank operations move one side only. Moving money from a wallet into a bank is
two operations, and **you are responsible for reversing one if the other
fails**:

```java
double amount = economy.quantiseForBank(100.0);
EconomyResult taken = economy.walletSpend(player, amount, "deposit to bank");
if (!taken.isOk()) { return; }

economy.bankDeposit(player, amount, "deposit from wallet", result -> {
    if (!result.isOk()) {
        economy.walletCredit(player, amount, "reversal: bank deposit failed");
        player.sendMessage(new TextComponentString(result.getMessage()));
    }
});
```

**Always `quantiseForBank` first.** A remote bank may be coarser than the
wallet — possibly whole units. Moving $354.50 into a whole-unit bank must move
$354 and leave the 50c in the wallet; rounding the bank side up would credit
money the wallet never gave up.

### Escrow

| Method | Scope | Notes |
|---|---|---|
| `EscrowResult escrowOpen(player, amount, reason)` | `escrow` | Debits the wallet, returns a ticket |
| `EconomyResult escrowRelease(ticket, recipient, reason)` | `escrow` | Pays the held amount to `recipient` |
| `EconomyResult escrowRefund(ticket, reason)` | `escrow` | Returns it to whoever put it up |
| `EconomyResult escrowForfeit(ticket, reason)` | `escrow` | **Destroys** the held money — a lost wager |
| `List<EscrowTicket> listOpenEscrows()` | `escrow` | Your own tickets only |

**Escrow never creates value.** A release moves exactly what was held. A payout
larger than the stake is a release *plus* a `walletCredit`. The one operation
that changes the money supply is `escrowForfeit`, which destroys it.

### The four ways a hold ends

Every ticket you open must end in exactly one of these. Picking the wrong one is
how a casino ends up paying out on losses:

| Outcome | Use it when | Where the money goes |
|---|---|---|
| `escrowRelease(ticket, player, …)` | The player won, or the pot goes to someone | That player's wallet |
| `escrowRefund(ticket, …)` | The round was cancelled or never resolved | Back to whoever staked it |
| `escrowForfeit(ticket, …)` | **The player lost and the house keeps the stake** | Destroyed |
| *(nothing)* | Your mod crashed or was removed | SUM's orphan sweep refunds it |

`escrowForfeit` destroys the money rather than banking it, matching how SUM's own
server shop treats its takings — there is no server account for it to land in.
If your house should *accumulate* the money, release the ticket to a house player
and keep those books yourself.

It is also the only settlement that needs **nobody online**, so a round can be
resolved after the player disconnects. Release and refund both need their
recipient present, because they pay a wallet.

Things worth knowing:

- **Tickets are handles, not values.** SUM looks every ticket up by id and uses
  its own stored amount and owner. Constructing an `EscrowTicket` with a
  different amount achieves nothing.
- **The recipient must be online** for a release or a refund. Otherwise
  `RECIPIENT_OFFLINE`, and the hold stays open.
- **A failed payout keeps the hold open** and returns an error. A closed ticket
  whose money never arrived would be money deleted.
- **Holds survive a restart.** Call `listOpenEscrows()` on server start to find
  anything a crash left behind, and refund it.
- **Abandoned holds are refunded for you.** If your mod is removed or
  de-authorized, SUM refunds its holds to the players after
  `orphanedEscrowGraceMinutes`.

---

## Worked example: a slot machine

Complete, and it compiles against the api jar alone.

```java
package com.example.mycasino;

import com.micatechnologies.minecraft.sum.api.*;
import java.util.Random;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

public class SlotMachine {

    private final EconomyHandle economy;
    private final Random random = new Random();

    public SlotMachine(EconomyHandle economy) {
        this.economy = economy;
    }

    /** Called on the server thread when a player pulls the lever. */
    public void pull(EntityPlayer player, double wager) {
        // 1. Take the stake and hold it. The money leaves the wallet now, so the
        //    player cannot spend it again while the reels spin, and a crash
        //    mid-spin will not lose it.
        EscrowResult bet = economy.escrowOpen(player, wager, "slot machine wager");
        if (!bet.isOk()) {
            if (bet.getFailure() == EconomyFailure.INSUFFICIENT_FUNDS) {
                tell(player, "You can't afford that bet.");
            } else if (bet.getFailure().isCallerError()) {
                // Our bug, not theirs. Log it; don't show a player a mystery.
                MyCasino.LOGGER.error("Escrow refused: {}", bet.getMessage());
                tell(player, "The machine is out of order.");
            } else {
                tell(player, bet.getMessage());
            }
            return;
        }
        EscrowTicket stake = bet.getTicket().get();

        // 2. Resolve the round.
        double multiplier = spin();

        if (multiplier <= 0.0) {
            // A loss: the house keeps the stake, so the held money is destroyed.
            // This is the branch to get right — releasing or refunding here
            // would hand the stake straight back and make every bet free.
            EconomyResult kept = economy.escrowForfeit(stake, "slot machine loss");
            if (!kept.isOk()) {
                // The hold is still open, so nothing is lost; it will be settled
                // by a retry or refunded by SUM's sweep.
                MyCasino.LOGGER.error("Could not settle a loss: {}", kept.getMessage());
            }
            tell(player, "No luck.");
            return;
        }

        // 3. A win. Give the stake back...
        EconomyResult returned = economy.escrowRelease(stake, player, "slot machine win");
        if (!returned.isOk()) {
            // The hold is still open, so nothing was lost. Try again later.
            MyCasino.LOGGER.error("Could not return stake: {}", returned.getMessage());
            tell(player, "Your payout is delayed. Your stake is safe.");
            return;
        }

        // 4. ...and pay the winnings on top. This is fresh money from the house,
        //    which is why it is a separate credit and not part of the release.
        double winnings = wager * multiplier;
        EconomyResult paid = economy.walletCredit(player, winnings, "slot machine payout");
        if (!paid.isOk()) {
            MyCasino.LOGGER.error("Could not pay winnings: {}", paid.getMessage());
            tell(player, "Your winnings could not be paid: " + paid.getMessage());
            return;
        }
        tell(player, String.format("You won $%.2f!", winnings));
    }

    private double spin() {
        int roll = random.nextInt(100);
        if (roll < 2) { return 10.0; }
        if (roll < 10) { return 2.0; }
        return 0.0;
    }

    private void tell(EntityPlayer player, String message) {
        player.sendMessage(new TextComponentString(message));
    }
}
```

### Recovering after a crash

```java
@Mod.EventHandler
public void serverStarting(FMLServerStartingEvent event) {
    SumEconomy.acquire("mycasino").ifPresent(economy -> {
        this.economy = economy;
        for (EscrowTicket stranded : economy.listOpenEscrows()) {
            // Nothing is going to settle a wager from before the restart.
            economy.escrowRefund(stranded, "server restarted mid-round");
        }
    });
}
```

A refund needs its owner online, so this refunds whoever is already on and
leaves the rest; call it again on player login, or let SUM's orphan sweep handle
them if your mod is gone entirely.

---

## Handling failures

`EconomyResult.getFailure()` tells you what happened.
`EconomyFailure.isCallerError()` splits them into "your bug" and "the player's
situation" — log the first, show the second.

| Failure | Caller error | What to do |
|---|---|---|
| `NOT_AUTHORIZED` | yes | You have no handle. Tell the operator to add you to `allowedMods` |
| `MISSING_SCOPE` | yes | Ask the operator for the scope, or disable the feature |
| `WRONG_THREAD` | yes | You called off the server thread. Fix your code |
| `CLIENT_SIDE` | yes | You called on a client. Guard with `!world.isRemote` |
| `INVALID_AMOUNT` | yes | Negative, `NaN`, infinite, or a null player |
| `NOT_REPRESENTABLE` | yes | Call `quantiseForBank` first |
| `ESCROW_NOT_FOUND` | yes | Unknown ticket, or not yours |
| `ESCROW_ALREADY_CLOSED` | yes | You settled it twice |
| `ECONOMY_UNAVAILABLE` | no | No backend. Disable your money features for now |
| `AMOUNT_TOO_LARGE` | no | Over the server's per-call cap. Tell the player |
| `INSUFFICIENT_FUNDS` | no | Tell the player |
| `BACKEND_REFUSED` | no | The backend said no. Do not retry |
| `BACKEND_ERROR` | no | Could not reach the backend. Retrying later is reasonable |
| `RECIPIENT_OFFLINE` | no | Wait for them; the hold is still safe |

`getMessage()` is always safe to show a player verbatim.

**Handle unknown values gracefully.** New failure reasons may be added without a
version bump — do not write an exhaustive `switch` that breaks on an unfamiliar
one.

---

## Events

SUM posts events on `MinecraftForge.EVENT_BUS`, on the server thread, **after**
money has moved. SUM's own transactions are posted too, attributed to `"sum"`.

| Event | Posted when |
|---|---|
| `WalletTransactionEvent` | Money left or entered a wallet (`SPEND` / `CREDIT`) |
| `BankTransactionEvent` | A bank movement **settled** (`DEPOSIT` / `WITHDRAW`) |
| `EscrowEvent` | A hold was `OPENED`, `RELEASED`, `REFUNDED`, `FORFEITED` or `ORPHAN_REFUNDED` |

```java
@SubscribeEvent
public void onWallet(WalletTransactionEvent event) {
    if (event.isSpend() && event.getAmount() >= 1000.0) {
        LOGGER.info("{} spent ${} (via {})", event.getPlayer().getName(),
            event.getAmount(), event.getSourceModId());
    }
}
```

Notes:

- Nothing is cancellable. By the time you see it, the money has moved.
- A listener that throws is logged and swallowed — one broken listener must not
  corrupt a completed transaction.
- **Reversals appear as their own events.** SUM reverses a spend by crediting it
  back, so a failed shop purchase shows as a `SPEND` followed by a matching
  `CREDIT`, not as nothing. Both really happened to the wallet.
- A wallet-to-bank move posts both a wallet and a bank event, because both
  balances changed.
- **A forfeit posts nothing if its owner is offline**, because every event names
  a player. The money is destroyed either way. Anything tracking how much is
  held should reconcile against `listOpenEscrows()` rather than assume it sees
  the close of every hold it saw open.

---

## For server operators

Everything lives in the `economy_integration` config category. It is separate
from `economy_api`, which configures the remote economy *service*.

| Key | Default | What it does |
|---|---|---|
| `allowedMods` | *(empty)* | `modid=scope,scope`. Empty denies every mod |
| `logTransactions` | `true` | Log every integration's money movements |
| `maxWalletTransaction` | `0` (no limit) | Per-call wallet cap for integrations |
| `maxBankTransaction` | `0` (no limit) | Per-call bank cap |
| `refundOrphanedEscrow` | `true` | Refund holds left by removed mods |
| `orphanedEscrowGraceMinutes` | `15` | How long to wait first |

### Commands

| Command | What it does |
|---|---|
| `/sum econ api mods` | Authorized mods, their scopes, and whether each is installed and connected |
| `/sum econ api escrow [modid]` | Money currently held, with ticket ids and ages |
| `/sum econ api refund <ticketId>` | Force-refund one hold to its owner |
| `/sum econ api reload` | Re-read config so an authorization change takes effect without a restart |

### If you run a remote economy service

Bank movements made by an integrating mod are recorded as `mod_deposit` and
`mod_withdraw`, with `sum.source_mod` in the transaction metadata naming the mod,
and a counterparty of `system:mod:<modid>`. They are deliberately distinct from
`atm_*` so an integration's activity can be audited separately from a player
standing at an ATM.

> **If your service declares `strictTransactionTypes`**, it will reject these
> two types until you add them. The protocol requires strict mode to be off by
> default, so this only affects services that deliberately turned it on.

---

## Versioning and stability

`SumEconomy.API_VERSION` is `1`.

- The `-api` jar **is** the API. If a class is not in it, it is not API, however
  public it looks — `...economy`, `...bank` and `...omceapi` are internals that
  change without notice.
- Existing signatures and enum constants will not be removed or change meaning
  without bumping `API_VERSION`.
- New methods, enum constants and failure reasons may be added **without** a
  bump. Do not switch exhaustively over `EconomyFailure`.
- Interfaces here are implemented by SUM. Do not implement them yourself; new
  methods may be added.

```java
if (!SumEconomy.isCompatible(1)) {
    throw new IllegalStateException("mycasino needs SUM economy API 1 or newer");
}
```

---

## Troubleshooting

**`acquire` returns empty.** Call `SumEconomy.describeDenial("yourmod")` and read
it — it names the exact config line to add. Otherwise: you asked before
`FMLServerStartingEvent`, or the server has no economy backend at all.

**Everything fails with `MISSING_SCOPE`.** The operator listed you without the
scope you need. `/sum econ api mods` shows what you were actually granted.

**Everything fails with `WRONG_THREAD`.** You are calling from a network handler
or a worker thread. Defer with
`player.getServer().addScheduledTask(() -> ...)`.

**A bank callback never fires.** It always fires, exactly once, on the server
thread. If you never see it, you are almost certainly waiting on a different
thread or the callback threw — exceptions from your callback are caught and
logged with your mod id.

**A hold is stuck open.** Something failed between opening and settling it.
`/sum econ api escrow` lists it; `/sum econ api refund <id>` returns it. Its
owner must be online.

**`getBankBalance` is empty but the player has money.** On a remote backend the
balance cache may not have answered for that player yet. It is not an error;
read it again shortly.
