# SUM Economy API — implementation plan

**Status:** Phases 1–7 and 9 complete; Phase 8 verified on the **local** backend
and outstanding on the **remote** one. Written and built 2026-08-09; reviewed,
extended, verified in game and committed 2026-08-11. Committed on `dev/ogh`
(not pushed).
**Owner:** Alex (mica-alex).

> **What is left:** the remote OMCE backend, plus three local scenarios the probe
> cannot script — a restart mid-hold, the orphan sweep, and SUM's own
> transactions posting events. The checklist is
> `docs/agent_progress/TESTING_PLAN.md` §4.14. Do not enable an economy
> integration on a production server running a *remote* economy until that half
> is verified, and do not tag a release before it — a tag is what consumers pin
> to.

This is the working plan for giving SUM a **public, stable economy API** that other
mods can compile and depend on. It is a phased build plan, not a design essay:
each phase has a checklist, an explicit "done when" bar, and enough detail that a
fresh agent session can pick it up cold from the resume prompt in §12.

Companion documents:

- `docs/agent_progress/TESTING_PLAN.md` — the canonical feature reference and
  playtest workflow for SUM as a whole. This plan does **not** duplicate it;
  §11 lists what to add there when this lands.
- `docs/OPEN_MCECONOMIC_API_SPECIFICATION.md` — the *remote service* wire
  protocol. Unrelated to this document despite the similar name. See the naming
  warning in §2.3.
- `docs/SUM_ECONOMY_API.md` — the consumer-facing API reference. Written in
  Phase 7; every code sample in it is verified to compile.

> Note on directory naming: SUM already has `docs/agent_progress/`. This plan
> lives in `docs/agent-plans/` because that is where it was asked for. If a
> second plan ever gets written, consolidate — don't grow two conventions.

---

## 0. Why this exists

The immediate driver is a **casino/gambling mod** for the Alto server that wants
players to wager real SUM money. That mod needs to read a balance, take a stake,
and pay out winnings, against *either* economy backend, without reaching into
SUM's internals.

Today it cannot. The audit that produced this plan found:

| Question | Answer today |
|---|---|
| Is there an `api` package? | No. `apiPackage` in `buildscript.properties:59` is empty. |
| Is there a stability contract? | No. `WalletService`, `BankService` and `EconomyBridge` are public-by-accident all-static utility classes. |
| Are economy events fired? | **None.** `grep` for `EVENT_BUS.post` across `src/main` returns zero hits. |
| Can a third party observe money moving? | Only by polling a balance. |
| Is there any authorization concept? | No. |
| Is there a transaction ledger? | Only on the remote backend. The local backend (`BankSavedData`) is a `UUID -> double` map with no history, and wallet spends record nothing at all. |

So a consumer mod today would have to reflect into internals and would break on
any refactor.

**What makes this tractable:** the internals are already shaped correctly.
`WalletService` is synchronous and always local. `BankService` already hides the
local-vs-remote split behind a uniform `Consumer<Result>` callback that is
*always* invoked on the server thread — inline for `BankSavedData`, after a round
trip for OMCE. One façade genuinely can serve both setups without the consumer
knowing which is running.

---

## 1. Scope

### In scope

- A new `com.micatechnologies.minecraft.sum.api` package: interfaces and data
  types only, published as a separate slim `-api` jar.
- Per-mod **authorization** driven by a new config category, deny-by-default,
  with per-capability scopes.
- **Wallet** read/spend/credit (synchronous).
- **Bank** read plus async deposit/withdraw, working identically on the local
  and remote backends.
- **Escrow**: crash-safe hold-and-resolve, which is the actual gambling
  primitive.
- **Forge events** posted on every money movement, including SUM's own.
- Attribution: every third-party transaction tagged with the calling mod id, in
  logs and in the remote ledger's metadata.
- `docs/SUM_ECONOMY_API.md` plus a worked casino example.

### Out of scope

- **Admin set-balance / adjust for third parties.** `BankService.adminSetBalance`
  and `adminAdjust` already refuse on the remote backend, so exposing them would
  hand consumers a capability that silently works on one backend and not the
  other. Locked out; see §3.
- **A local transaction ledger.** Worth having, but it is its own feature with
  its own storage and retention questions. The API is designed so it can be
  bolted on later (every mutation already flows through one choke point).
- **Client-side API.** Consumers get server-side calls only. Balance *display*
  on a client already works through SUM's existing sync packets.
- **Offline-player credits.** Escrow release requires an online recipient. See
  the limitation in §6.5.

---

## 2. Locked design decisions

Settled with Alex on 2026-08-09. Don't re-litigate without an explicit ask.
Each line is one decision plus the reason.

### 2.1 Authorization

- **Deny by default, with scopes.** An empty `allowedMods` list means no third
  party can touch the economy. Entries use SUM's existing `key=value` config
  idiom — `casino=wallet_read,wallet_write,escrow` — matching `roadrunner` and
  `loyalty`.
- **This is an operator control, not a security boundary. Say so in the docs.**
  Any mod on the server can already reflect into `WalletService` and move money;
  no in-JVM check can stop that. What the allowlist actually buys is (a) an
  operator can revoke one integration without a code change, (b) a mod that
  forgets to ask fails loudly at startup instead of silently half-working, and
  (c) every transaction carries an attributable mod id. Overselling this as
  "security" would be the single most misleading thing this feature could do.
- **Mod id is self-declared.** `SumEconomy.acquire("casino")` takes the caller's
  word. Stack-walking to verify the caller's jar is fragile under
  LaunchWrapper/Mixin transformation and would break under shading. Rejected.
- **Denial is loud.** A refused `acquire` logs at WARN with the mod id and the
  exact config line the operator would need to add.

### 2.2 API surface

- **Wallet write, bank read *and* write, escrow. No admin.** Rationale in §1.
- **Interfaces and immutable data types only in the `api` package.** Every
  implementation class lives in `com.micatechnologies.minecraft.sum.economy.apiimpl`
  and never ships in the api jar. If it isn't in the api jar, it isn't API.
- **No `Double.NaN` in the public surface.** SUM's internals return `NaN` for
  "unknown balance", which is a trap for consumers (`NaN >= x` is always false,
  silently). The API returns `OptionalDouble` instead.
- **Every mutating call returns a result object, never a bare boolean.** A
  consumer that gets `false` back needs to know whether it was insufficient
  funds, a missing scope, a wrong thread, or a dead backend — those want
  different handling and different player-facing messages.

### 2.3 Naming

- **The new config category is `economy_integration`, not an addition to
  `economy_api`.** `economy_api` is already the *remote OMCE service client*.
  Putting a third-party mod allowlist in it would create exactly the kind of
  confusion CLAUDE.md already warns about with wallet-vs-bank. Two different
  things named "economy api" in one config file is a bug factory.
- Consumer-facing docs say "SUM Economy API"; the remote protocol keeps the
  name "Open MCEconomic API". The Phase 7 doc opens by distinguishing them.

### 2.4 Threading

- **All mutating calls must be on the server thread.** The API checks
  (`server.isCallingFromMinecraftThread()`) and *refuses with an explicit error
  result* rather than corrupting state. `WalletService` mutates inventory and
  capabilities with no locking; an off-thread call today is a silent data race.
- **Bank callbacks fire on the server thread**, inherited from `BankService`.
  The API contract states this so consumers can touch world state in a callback.

### 2.5 Events

- **Post-only in v1.** Observation is safe. A cancellable `Pre` is genuinely
  useful (tax mods, anti-cheat) but requires auditing every internal call site
  for correct handling of a newly-failing spend. Deferred to Phase 9 as an
  explicit decision, not an oversight.
- **SUM's own transactions fire events too**, with `sourceModId = "sum"`. An
  event bus that only sees third-party money is useless for anything that wants
  a complete picture.

### 2.6 Escrow

- **Escrow never mints value.** `escrowOpen` debits the player; release moves
  *that held amount* and nothing more. A casino win larger than the stake is
  `escrowRelease` (stake back or to the house) **plus** a separate
  `walletCredit` for the winnings. Any other model lets a buggy consumer print
  money. The one operation that changes the money supply is `escrowForfeit`,
  which shrinks it — see the Phase 5 note dated 2026-08-11.
- **Escrow is persisted** in a `WorldSavedData`, so a crash mid-spin doesn't eat
  a stake.
- **Orphaned tickets auto-refund.** If a ticket's owning mod is gone or
  de-authorized at server start, refund it after a grace period rather than
  stranding the money.

---

## 3. Rejected alternatives

Recorded so they don't get proposed again.

| Rejected | Why |
|---|---|
| Allow-all when `allowedMods` is empty | A server that never reads the config would ship with an open economy. |
| Flat modid allowlist with no scopes | A mod let in for wallet bets could also drain bank accounts. |
| Stack-walking to verify caller identity | Fragile under LaunchWrapper/Mixin/shading; gives false confidence. |
| Exposing `adminSetBalance` / `adminAdjust` | Already refuses on the remote backend — a capability that works on one backend only. |
| Consumers compiling against the full SUM jar | No compile-time signal about what's supported; nothing discourages reaching into internals. |
| Reusing `WalletService`/`BankService` directly as the public API | They are static utilities with no versioning, no authorization hook, no thread guard, and `NaN` in the return contract. |
| Skipping escrow (`spend` + `credit` only) | Every consumer reinvents crash-safe wagering; a restart mid-round silently loses a stake. |
| Cancellable `Pre` events | **Dropped in Phase 9**, not merely deferred. See the reasoning there. |
| `escrowRelease(ticket, null, …)` meaning "the house keeps it" | A null recipient is far likelier to be a consumer's bug than an intention; silently burning a stake on one is the worst possible reading. It is `escrowForfeit`. |

---

## 4. Target architecture

```
src/main/java/com/micatechnologies/minecraft/sum/
├── api/                              # <- the -api jar. Interfaces + data only.
│   ├── SumEconomy.java               #    static entry point
│   ├── EconomyHandle.java            #    the authorized per-mod handle
│   ├── EconomyScope.java             #    enum of capabilities
│   ├── EconomyResult.java            #    immutable outcome + failure reason
│   ├── EconomyFailure.java           #    enum: NO_SCOPE, INSUFFICIENT_FUNDS, ...
│   ├── EconomyStatus.java            #    backend kind, currency symbol, scale
│   ├── EscrowTicket.java             #    opaque handle to a held amount
│   └── event/
│       ├── SumEconomyEvent.java      #    base Forge Event
│       ├── WalletTransactionEvent.java
│       ├── BankTransactionEvent.java
│       └── EscrowEvent.java
└── economy/
    └── apiimpl/                      # never in the api jar
        ├── EconomyApiRegistry.java   #    acquire(), authorization, handle cache
        ├── EconomyAuthorizer.java    #    allowlist parsing + scope checks
        ├── EconomyHandleImpl.java    #    the real implementation
        ├── EscrowService.java        #    ticket lifecycle
        ├── EscrowSavedData.java      #    WorldSavedData persistence
        └── EconomyEvents.java        #    event posting helpers
```

### 4.1 Entry point

```java
public final class SumEconomy {
    public static final int API_VERSION = 1;

    /** Empty when the caller is not authorized, or when no economy backend exists. */
    public static Optional<EconomyHandle> acquire(String modId);

    /** Why acquire() would fail, for diagnostics. Null when it would succeed. */
    @Nullable public static String describeDenial(String modId);

    public static boolean isEconomyAvailable();
    public static EconomyStatus getStatus();
}
```

### 4.2 The handle

```java
public interface EconomyHandle {
    String getModId();
    Set<EconomyScope> getScopes();
    boolean hasScope(EconomyScope scope);
    EconomyStatus getStatus();

    // --- wallet: synchronous, server thread only ---
    OptionalDouble getWalletBalance(EntityPlayer player);
    boolean canAfford(EntityPlayer player, double amount);
    EconomyResult walletSpend(EntityPlayer player, double amount, String reason);
    EconomyResult walletCredit(EntityPlayer player, double amount, String reason);

    // --- bank: async, callback on the server thread ---
    OptionalDouble getBankBalance(EntityPlayer player);
    double quantiseForBank(double amount);
    void bankDeposit(EntityPlayer player, double amount, String reason,
                     Consumer<EconomyResult> callback);
    void bankWithdraw(EntityPlayer player, double amount, String reason,
                      Consumer<EconomyResult> callback);

    // --- escrow ---
    EscrowResult escrowOpen(EntityPlayer player, double amount, String reason);
    EconomyResult escrowRelease(EscrowTicket ticket, EntityPlayer recipient, String reason);
    EconomyResult escrowRefund(EscrowTicket ticket, String reason);
    List<EscrowTicket> listOpenEscrows();
}
```

### 4.3 Results

```java
public final class EconomyResult {
    public boolean isOk();
    @Nullable public EconomyFailure getFailure();
    /** Player-facing text on failure. Never null when !isOk(). */
    @Nullable public String getMessage();
    /** Resulting balance where meaningful. */
    public OptionalDouble getBalance();
}

public enum EconomyFailure {
    NOT_AUTHORIZED, MISSING_SCOPE, ECONOMY_UNAVAILABLE, WRONG_THREAD,
    CLIENT_SIDE, INVALID_AMOUNT, AMOUNT_TOO_LARGE, INSUFFICIENT_FUNDS,
    NOT_REPRESENTABLE, BACKEND_REFUSED, BACKEND_ERROR,
    ESCROW_NOT_FOUND, ESCROW_ALREADY_CLOSED, RECIPIENT_OFFLINE
}
```

### 4.4 Config category

New category `economy_integration` in `SumConfig.java`:

| Key | Type | Default | Meaning |
|---|---|---|---|
| `allowedMods` | String list | *(empty)* | `modid=scope,scope`. Empty = deny all. `modid=*` grants every scope. |
| `logTransactions` | boolean | `true` | Log every third-party mutation at INFO with `[economy-api]`. |
| `maxWalletTransaction` | double | `0.0` | Per-call cap for third parties. `0` = unlimited. |
| `maxBankTransaction` | double | `0.0` | Same, for bank moves. |
| `escrowRefundOrphaned` | boolean | `true` | Auto-refund tickets whose mod is gone/de-authorized. |
| `escrowOrphanGraceMinutes` | int | `15` | How long to wait before that refund. |

Scope tokens: `wallet_read`, `wallet_write`, `bank_read`, `bank_write`,
`escrow`, and `*`. `wallet_write` implies `wallet_read`; `bank_write` implies
`bank_read`; `escrow` implies `wallet_write`.

---

## 5. Phase overview

| # | Phase | Deliverable | Depends on |
|---|---|---|---|
| 1 | API surface + build plumbing | `api` package compiles; `-api` jar is produced | — |
| 2 | Authorization + config | `acquire()` gates correctly; parser unit-tested | 1 |
| 3 | Wallet + bank implementation | Real money moves through the handle | 2 |
| 4 | Events | Every mutation posts an event, SUM's included | 3 |
| 5 | Escrow | Crash-safe hold/release/refund/forfeit | 3 |
| 6 | Admin + ops tooling | `/sum econ api …` subcommands | 2, 5 |
| 7 | Documentation | `docs/SUM_ECONOMY_API.md` + casino example | 3, 4, 5 |
| 8 | Consumer validation | A real mod compiles against the api jar and transacts | 7 |
| 9 | Wrap-up + deferred items | TESTING_PLAN updated; `Pre` events decided | 8 |

---

## 6. Phases in detail

### Phase 1 — API surface and build plumbing ✅ DONE 2026-08-09

**Goal:** the shape exists and the build emits a consumable artifact. No
behaviour yet — every method returns a well-formed "unavailable" result.

- [x] Set `apiPackage = api` in `buildscript.properties`. **Note the ordering
      trap:** `build.gradle:126-140` validates that the api source directory
      already exists and fails configuration otherwise, so the classes must land
      *before* this property is set.
- [x] Run `JAVA_HOME="..." ./gradlew build` and confirm a
      `uia-server-utility-mod-<ver>-api.jar` appears in `build/libs/`.
- [x] Confirm the api jar contains **only** `com/micatechnologies/minecraft/sum/api/**`
      (`jar tf`). Verified: 9 `.java` sources + 10 `.class` files, nothing else.
- [x] Create `api/EconomyScope.java` — enum with the implication rules from §4.4
      expressed as an `expand()` helper (pure, unit-testable).
- [x] Create `api/EconomyFailure.java` and `api/EconomyResult.java`. `EconomyResult`
      is immutable with static factories `ok(...)` / `fail(EconomyFailure, String)`.
- [x] Create `api/EconomyStatus.java` — `isAvailable()`, `isRemoteBank()`,
      `isBankDegraded()`, `getCurrencySymbol()`, `getBankMinorUnitDigits()`,
      `getApiVersion()`.
- [x] Create `api/EscrowTicket.java` — immutable value type, equality by id.
- [x] Create `api/EscrowResult.java` — result plus `Optional<EscrowTicket>`.
- [x] Create `api/EconomyHandle.java` per §4.2, with Javadoc on **every** method
      stating the thread requirement and the required scope.
- [x] Create `api/SumEconomy.java` per §4.1. Uses a `SumEconomy.Provider` SPI
      installed on server start, so the implementation can live outside the api
      package; `acquire` returns `Optional.empty()` until Phase 2 installs one.
- [x] Javadoc `package-info.java` for `api` stating the stability contract.

Added beyond the original checklist:

- [x] **Wire `apiJar` into `build`** (`addon.gradle`). The GTCEu buildscript
      registers the task but only attaches it to the artifact set under CI or
      `deploymentDebug`, so a local `./gradlew build` produced every other jar
      and silently skipped this one — no api jar, no error.
- [x] **Build-time API isolation guard** (`addon.gradle`). Fails `apiJar` if any
      class in the api package imports SUM internals, naming file, line and
      import. Without it the leak compiles cleanly here (the whole mod is on this
      project's classpath) and only fails in a *dependent* mod's build, or at
      runtime with a `NoClassDefFoundError` on someone's server. Verified by
      deliberately introducing a leak and confirming the failure.
- [x] **Unit tests** — `api/EconomyScopeTest.java` (11 tests: token parsing,
      implication closure, idempotence, unmodifiability) and
      `api/EconomyResultTest.java` (11 tests: NaN suppression, message
      guarantees, escrow result invariants). These caught a real bug:
      `EconomyResult.fail(null)` threw `NullPointerException` from its own
      message lookup before reaching the guard meant to reject it.
- [x] **Release workflow** publishes the api jar
      (`build-mod-release-pre-release-main.yml`): copy step that hard-fails if
      the jar is absent, SHA-256, attached to both release and pre-release, and
      a body note telling players not to put it in `mods/`.
- [x] **PR workflow** asserts the api jar was produced and uploads it as an
      artifact (`test-mod-build-pr.yml`), so a change that stops producing it
      fails in review rather than at release time.

**Done when:** `./gradlew build` is green, the api jar exists and is
implementation-free, and the whole surface is documented. No functional change
to the running mod. — **All met.** 340 tests pass; SUM's runtime behaviour is
unchanged, since nothing installs a `Provider` yet.

> ⚠️ **Build JDK:** use **17 or 21**, not 25/26. Gradle 8.9's Groovy cannot read
> Java 25 class files — adding typed closure parameters to `addon.gradle` under
> JDK 25 fails configuration outright with
> `BUG! exception in phase 'semantic analysis' ... Unsupported class file major
> version 69`. CLAUDE.md previously described 23–26 as producing only harmless
> warnings; that has been corrected.

---

### Phase 2 — Authorization and config ✅ DONE 2026-08-09

**Goal:** `acquire()` correctly grants or denies based on config.

- [x] Add the `economy_integration` category to `SumConfig.java`, following the
      existing declaration triplet convention exactly.
- [x] Write `private static Map<String, Set<EconomyScope>> parseAllowedMods(String[])`
      in `SumConfig`, matching the style of `parseSpeedBlocks` /
      `parseLoyaltyMilestones`. Malformed lines log a warning naming the bad line
      and are skipped, never fatal.
- [x] Add getters: `getEconomyIntegrationScopes(modId)`,
      `getEconomyIntegrationAllowedMods()`, `isEconomyIntegrationLoggingEnabled()`,
      `getEconomyIntegrationMaxWalletTransaction()`,
      `getEconomyIntegrationMaxBankTransaction()`,
      `isEconomyIntegrationRefundOrphanedEscrow()`,
      `getEconomyIntegrationOrphanedEscrowGraceMinutes()`.
- [x] Put a worked example in the config comment itself.
- [x] Create `economy/apiimpl/EconomyAuthorizer.java`.
- [x] Create `economy/apiimpl/EconomyApiRegistry.java`.
- [x] Wire `SumEconomy.acquire` / `describeDenial` to the registry, installed on
      `FMLServerStartingEvent` **after** the backend is chosen and uninstalled on
      `FMLServerStoppedEvent` **before** it is torn down.
- [x] Log every successful `acquire` at INFO with the granted scopes, and every
      denial at WARN with the exact config line to add — once per mod id per
      session, so a mod retrying every tick cannot flood the log.
- [x] Update `docs/examples/example_config.cfg` with the new category.

**Tests** — 11 added to `SumConfigParsersTest`, all passing: scope parsing,
wildcard, implication expansion, case/whitespace tolerance, unknown token
skipped with the rest of the line kept, malformed lines skipped, duplicate mod
id (last wins), one bad line not costing the others, empty input denying
everything, and granted scope sets being unmodifiable.

**Done when:** an authorized mod id gets a handle with the right scopes, an
unlisted one gets `Optional.empty()` and a WARN naming the fix, and the parser
tests pass. — **Met.**

Decisions taken during implementation, worth keeping:

- **Scopes are read live, not frozen into the handle.** `EconomyHandleImpl`
  stores only a mod id and re-reads its grants on every call. Freezing them at
  acquire time would mean an operator revoking a mod sees no effect until the
  next restart — the mod would keep spending player money for the rest of the
  session, which defeats the point of a revocable allowlist. Handles can
  therefore be cached forever without an invalidation path.
- **An entry granting no valid scopes is dropped, not stored empty.** Storing it
  would report the mod as authorized while every call it made failed — the
  worst of both messages for whoever is debugging it.
- **The guard is implemented in full** (scope → null player → client side →
  server thread → finite → non-negative → within cap), ordered so the most
  actionable failure wins. It is the single choke point every mutation passes
  through, and where a future local ledger hooks in.

> ⚠️ **This build cannot move money yet, by design.** The wallet, bank and
> escrow operations run the guard and then return
> `ECONOMY_UNAVAILABLE` from `EconomyHandleImpl.unfinished()`, which also logs
> once per mod at ERROR. Phase 3 replaces those bodies. Do not ship a release
> from this state to a server running economy integrations.

---

### Phase 3 — Wallet and bank implementation ✅ DONE 2026-08-09

**Goal:** real money moves, on both backends, with every guard in place.

- [x] Create `economy/apiimpl/EconomyHandleImpl.java`.
- [x] Implement a single `guard(...)` run before **every** mutation. Ordered
      scope → player non-null → server side → server running → server thread →
      finite → non-negative → within cap, so the most actionable failure wins.
- [x] `getWalletBalance` / `getBankBalance` return `OptionalDouble.empty()` where
      the internals return `NaN`.
- [x] `walletSpend` / `walletCredit` over `WalletService`, rounding to cents.
- [x] **Refactor:** created `economy/MoneyMath.java` holding the one rounding
      rule; `MoneyTransfer.roundToCents` now delegates to it and stays
      package-private for its existing tests.
- [x] `bankDeposit` / `bankWithdraw` over `BankService`, quantising first and
      translating `BankService.Result` into `EconomyResult`.
- [x] `quantiseForBank` as a straight delegate.
- [x] **Attribution:** reasons are prefixed `[<modId>] ` and truncated to 256
      chars; remote transactions carry `metadata("sum.source_mod", modId)` and a
      counterparty of `OmceParty.system("mod:<modId>")`. Added
      `BankService.deposit/withdraw` overloads taking a transaction type and a
      metadata map; the existing 5-arg signatures delegate unchanged.
- [x] **New transaction types** `TX_MOD_DEPOSIT` / `TX_MOD_WITHDRAW`, so a
      ledger can tell an integration's move from a player at an ATM. The spec's
      type vocabulary is explicitly open ("a service MUST NOT reject an
      unrecognised type") — see the caveat below.
- [x] Bank callback invoked **exactly once** on every path, including guard
      rejection, with consumer exceptions caught and logged.
- [x] Honour `logTransactions`, prefixed `[economy-api]`. Caller errors log at
      WARN **even with audit logging off** — a missing scope or off-thread call
      is a bug an operator needs to see, not routine traffic they opted out of.

**Tests** — 17 added, all passing: `MoneyMathTest` (7, including that
`MoneyTransfer` still rounds through the same rule) and
`EconomyHandleImplTest` (10: failure classification, result translation, reason
attribution and truncation). The pre-existing `MoneyTransferTest` still passes
unchanged, which is what makes the rounding refactor safe.

**Done when:** a test consumer can spend and credit a wallet, and deposit and
withdraw from a bank, with identical code on both backends; every guard has a
distinct failure code; and no path can leak `NaN` or skip a callback. —
**Code complete; the consumer half is Phase 8's job.** 477 tests pass and the
dedicated server still boots after the `BankService` changes.

Decisions taken during implementation:

- **Bank failures are classified by error code, not message text.**
  `BankService.Result` gained a nullable `failureCode` carrying an
  `OmceProtocol.ERR_*` value, set where the cause is actually known (local
  shortfall, remote error code, unrepresentable amount). Without it the API
  would have had to match on player-facing wording to tell "you're broke" from
  "the bank is down" — which breaks silently the next time that wording is
  improved. This is additive; existing callers reading `ok`/`error` are
  unaffected.
- **`BACKEND_ERROR` vs `BACKEND_REFUSED` is a real distinction**: being unable
  to *ask* the backend is worth retrying, the backend *saying no* is not.
- **An amount that quantises to zero fails** with `NOT_REPRESENTABLE` rather
  than silently succeeding while moving nothing.

> ⚠️ **Escrow still refuses**, loudly, via `escrowUnavailable()` — Phase 5.
> Wallet and bank are fully wired.

> ⚠️ **`strictTransactionTypes` services.** A remote economy service running in
> strict mode will reject `mod_deposit` / `mod_withdraw` with
> `UNSUPPORTED_TRANSACTION_TYPE` until told about them. The spec requires strict
> mode to be off by default and declared as a capability, so this affects only
> deliberately-strict deployments — but it belongs in the Phase 7 docs and is
> worth a startup warning. Added to §10 as an open question.

---

### Phase 4 — Events ✅ DONE 2026-08-09

**Goal:** anything can observe money moving, including SUM's own transactions.

- [ ] Create `api/event/SumEconomyEvent.java` extending
      `net.minecraftforge.fml.common.eventhandler.Event`, carrying `EntityPlayer`,
      `String sourceModId` (`"sum"` for internal), `double amount`, `String reason`.
- [ ] `WalletTransactionEvent` with a `Type` enum (`SPEND`, `CREDIT`) and the
      resulting balance.
- [ ] `BankTransactionEvent` with `Type` (`DEPOSIT`, `WITHDRAW`), posted **after**
      settlement only — never on a request that failed or is still in flight.
- [ ] Create `economy/apiimpl/EconomyEvents.java` with post helpers that swallow
      and log subscriber exceptions. A crashing listener must not roll back a
      completed transaction.
- [ ] Post from `WalletService.spend` / `credit` on success — this covers SUM's
      shops, plots, jobs, `/pay`, loyalty and ATM in one place.
- [ ] Post from `BankService` in the success branch of both `applyLocal` and the
      remote callback.
- [ ] **Carried over from Phase 5:** post `EscrowEvent` from `EscrowService` on
      open, release, refund, and orphan-refund. Four sites, each already the
      point where the ticket is recorded or removed: `open` after `data.put`,
      `close` after `data.remove`, and the two refund paths in `sweepOrphans`.
- [ ] Confirm posting is server-thread only.

**Done when:** a listener registered on `MinecraftForge.EVENT_BUS` sees a shop
purchase, a `/pay`, a loyalty reward, an ATM deposit, and a third-party call,
each correctly attributed.

**Watch out:** `WalletService.spend` has a rollback path
(`TileEntityShop.java:269` credits back on failure). Don't post a SPEND event for
a transaction that gets reversed a line later — or accept that the reversal
posts its own CREDIT and document that pairing. Pick one and write it down.

---

### Phase 5 — Escrow ✅ DONE 2026-08-09

**Goal:** the gambling primitive, crash-safe.

- [x] Create `economy/apiimpl/EscrowSavedData.java extends WorldSavedData`,
      following `BankSavedData`. Stores per ticket: id, owner UUID, amount,
      owning mod id, reason, opened-at epoch millis.
- [x] Create `economy/apiimpl/EscrowService.java`:
  - [x] `open` — guard, `WalletService.spend`, persist the ticket, return it.
        If the spend fails, no ticket is created.
  - [x] `release(ticket, recipient)` — close the ticket, `WalletService.credit`
        the recipient. If the credit fails, **keeps the ticket open** and returns
        an error; a closed ticket whose money never landed is money deleted.
  - [x] `refund(ticket)` — release back to the original owner.
  - [x] `listOpen(modId)` — the mod's own tickets only.
- [x] Recipient offline → `RECIPIENT_OFFLINE`, ticket stays open.
- [x] Ticket IDs are `UUID`s; release on an unknown or closed ticket fails with
      `ESCROW_NOT_FOUND` / `ESCROW_ALREADY_CLOSED` and never moves money.
- [x] Orphan sweep: any ticket whose owning mod is not loaded or no longer
      authorized is refunded once `orphanedEscrowGraceMinutes` has elapsed since
      server start, when `refundOrphanedEscrow` is true. Every refund logs at
      WARN. Owner offline → deferred and retried on their next login.
- [x] Post `EscrowEvent` on open/release/refund. *(Was deferred to Phase 4, which
      added the four posts: `open`, `close` for both release and refund,
      `adminForceRefund`, and the orphan sweep.)*
- [x] Wire the escrow methods on `EconomyHandleImpl`, scoped to `ESCROW`.
- [x] `escrowForfeit` — added 2026-08-11; see below.

**Tests** — 16 added, all passing: `EscrowSavedDataTest` (8: NBT round trip
preserving amount/owner/mod, many tickets, empty store, unusable entries
dropped, remove idempotence, per-mod isolation, oldest-first ordering, null id)
and `EscrowOrphanSweepTest` (8: missing mod orphaned, grace period blocking,
the due boundary, active mod left alone, selectivity, ordering, empty/null
input, null ticket skipped). 493 tests total; server boots clean.

Decisions taken during implementation:

- **Caller-supplied tickets are never trusted.** `EscrowTicket` is a public
  value type with a public constructor, so a consumer can fabricate one claiming
  any amount. Every operation looks the ticket up by id in the world save and
  uses the *stored* amount and owner; the object passed in supplies nothing but
  an id. Without this, `escrowRelease(new EscrowTicket(id, me, 1_000_000, …))`
  would mint money.
- **Another mod's ticket reports `ESCROW_NOT_FOUND`, not a permission error.**
  Whether some other integration holds a ticket is not this caller's business,
  and a distinct error would let one mod probe another's state.
- **The orphan grace runs from server start, not from ticket age.** The case it
  guards is an operator pulling a mod out for one restart; refunding every
  in-flight wager the instant that happens is worse than waiting.
- **The sweep idles.** It stops scanning once nothing is left to do, and is
  re-armed by a player login or a config reload, so a server with no
  integrations pays a counter compare per tick and nothing else.
- **Renamed `EscrowSavedData.get(UUID)` to `getTicket(UUID)`** — it was
  ambiguous against the static `get(World)` factory at any call site passing
  null.

#### `escrowForfeit`, added 2026-08-11

Reviewing the finished API against its own driving use case turned up a hole:
**a casino could not take a losing wager.** The only ways to close a hold were
release and refund, and both pay a wallet. The worked slot-machine example in
`docs/SUM_ECONOMY_API.md` had quietly papered over it — its loss branch released
the stake to a "house account" helper that returned the player themselves, so
every documented loss handed the money straight back. That is not a compile
error anywhere; it is a house that never wins.

This also answers the third open question in §10 (where house-edge money goes):

- `escrowForfeit(ticket, reason)` closes the ticket and **destroys** the money,
  matching how SUM's server shop already treats its takings. There is no server
  account for it to land in, and inventing one is a bigger feature than this.
  An operator who wants the house to accumulate money releases to a house player
  and keeps those books themselves.
- **A separate named method, not `escrowRelease(ticket, null, …)`.** A null
  recipient arriving at a release is far more likely to be a consumer's bug than
  an intention, and silently burning a player's stake on one would be the worst
  possible reading of it. A `EscrowSettlementContractTest` pins this: exactly one
  `escrowRelease` overload, and it requires a recipient.
- **It needs nobody online**, unlike release and refund. A round has to be
  settleable after the player disconnects, or every disconnect is a free bet.
  The cost is that no `EscrowEvent` is posted when the owner is offline, since
  every economy event names a player — documented in both `EscrowEvent` and the
  consumer doc, with the advice to reconcile against `listOpenEscrows()`.
- New `EscrowEvent.Type.FORFEITED`, so a listener tracking the money supply can
  tell a destroyed stake from a paid-out one. Additive, so `API_VERSION` stays 1.

**Done when:** open → release and open → refund both conserve value exactly; a
`/stop` between open and release leaves the ticket intact on restart; and an
orphaned ticket refunds itself. — **Persistence and selection are proven by
unit test; the live money movement needs a consumer (Phase 6 tooling or
Phase 8).** See the note below.

> ⚠️ **Escrow moves real money and has not yet been exercised at runtime.** The
> unit tests cover persistence and the orphan rule, but no test opens a hold
> against a live player. Do not enable an escrow-scoped integration on a
> production server before Phase 6's `/sum econ api escrow` commands or Phase 8's
> consumer validation has exercised open → release → refund in game.

---

### Phase 6 — Admin and ops tooling ✅ DONE 2026-08-09

**Goal:** an operator can see and fix API state without a debugger.

- [ ] `/sum econ api mods` — every authorized mod, its scopes, and whether it has
      acquired a handle this session.
- [ ] `/sum econ api escrow [modid]` — open tickets with owner, amount, age.
- [ ] `/sum econ api refund <ticketId>` — force-refund one ticket. Op-only.
- [ ] `/sum econ api reload` — re-read `economy_integration` and invalidate the
      handle cache, so revoking a mod doesn't need a restart.
- [ ] Follow `CommandSum.java`'s existing permission and usage-text conventions;
      add lang keys to `en_us.lang`.

**Done when:** every command works and the usage text matches the rest of
`/sum`.

---

### Phase 7 — Documentation ✅ DONE 2026-08-09

**Goal:** `docs/SUM_ECONOMY_API.md`, good enough that the casino mod gets
written without reading SUM's source.

- [ ] **Opening section distinguishing this from the Open MCEconomic API.** The
      names are similar and confusing them wastes a reader's afternoon.
- [ ] Wallet vs bank vs escrow, reusing CLAUDE.md's table. A consumer author who
      confuses wallet and bank will write a broken mod.
- [ ] Quick start: Gradle dependency on the `-api` jar, `mcmod.info` /
      `@Mod(dependencies = "required-after:sum")`, and the `acquire` call.
- [ ] Config reference for `economy_integration`, with the scope table and a
      worked `allowedMods` example.
- [ ] **A plainly-worded note that the allowlist is operator authorization and
      audit attribution, not a security boundary**, and why (§2.1).
- [ ] Full method reference: signature, required scope, thread, failure modes.
- [ ] Threading and lifecycle: when to acquire, server-side only, callbacks on
      the server thread.
- [ ] Events reference with a listener example.
- [ ] **Worked example: a slot machine.** Read balance → `escrowOpen` the stake →
      resolve → on win `escrowRelease` to the player plus `walletCredit` the
      winnings; on loss `escrowRelease` to the house. Complete, compilable, with
      error handling — this is the section that will actually get read.
- [ ] Error-handling guidance: what to show a player per `EconomyFailure`.
- [ ] Versioning and stability policy: what `API_VERSION` bumps mean.
- [ ] Troubleshooting: acquire returns empty, calls fail with `WRONG_THREAD`,
      bank calls never call back, escrow stuck open.

**Done when:** the doc is complete and every code sample compiles against the
real api jar (verify in Phase 8 — untested samples rot immediately).

---

### Phase 8 — Consumer validation ⚠️ PARTLY DONE 2026-08-09

**Goal:** prove the artifact actually works from outside.

- [x] Build a throwaway consumer mod in a scratch directory, depending only on
      the `-api` jar at compile time.
- [x] Confirm it compiles with **no** SUM implementation classes on the compile
      classpath. *(Verified by inspecting the javac classpath: it carries the
      `-api` jar and Forge, and `build/classes/java/main` is absent.)*
- [x] Drop it in `run/mods` alongside SUM; verify `acquire` denies it until it's
      added to `allowedMods`, then grants the exact scopes configured.
      *(24/24 startup checks pass on a dedicated server, re-run 2026-08-11.)*
- [x] Exercise every method against the **local** backend: wallet read/spend/
      credit, escrow open/release/refund/forfeit. **`/casinoprobe` passed 32/32
      in a dev client, 2026-08-11.** Bank deposit/withdraw is covered only as a
      correct `MISSING_SCOPE` refusal, since the probe holds `escrow` alone —
      the successful bank path arrives with the remote-backend run below.
- [ ] Exercise the same against the **remote** OMCE backend. This is the highest-
      value test in the plan — it is the claim "works with both setups", and it
      is the one most likely to be quietly false. Watch quantisation on a
      whole-unit currency and the ambiguity-recovery path.
- [ ] Kill the server between `escrowOpen` and release; confirm the ticket
      survives and the money is not duplicated.
- [ ] Verify events fire for both SUM-internal and third-party transactions.
      **Third-party half done** — 19 movements observed with correct types,
      amounts and `mycasino` attribution, including `FORFEITED`. SUM's own
      transactions have not been watched yet.
- [ ] Verify remote ledger entries carry the `sum.source_mod` metadata.
- [x] Copy each doc sample into the consumer mod and confirm it compiles.
      *(Both samples are literally probe source files — `SlotMachine.java` and
      `CrashRecoveryExample.java` — so the document cannot drift from something
      that builds.)*

**Done when:** every box is ticked on both backends. The five that remain all
need a live player; nothing else is blocking them.

---

### Phase 9 — Wrap-up and deferred items ✅ DONE 2026-08-11

- [x] Update `docs/agent_progress/TESTING_PLAN.md`: add the API to §1
      (where everything lives), the locked decisions to §2, a playtest checklist
      to §4, and mark the relevant §8 entry shipped with the date.
- [x] Update `CLAUDE.md`'s Architecture Overview with the `api/` package and the
      `economy_integration` category.
- [x] Update the source-layout tree in CLAUDE.md.
- [x] **Decide on cancellable `Pre` events — DROPPED for v1.**
- [x] Decide on a **local transaction ledger — deferred**, deliberately.
- [ ] Tag a release so consumers have a version to depend on. **Alex's call**,
      and it should wait for the in-game verification in Phase 8: a tagged
      release is a version other mods pin to, so tagging one whose money paths
      have never run would be publishing an untested contract.

#### Why cancellable `Pre` events were dropped

Shipping them would mean every internal `WalletService.spend` call site — shops,
plots, job escrow, `/pay`, loyalty, the ATM — correctly handling "a third party
said no" partway through its own multi-step transaction. Several of those sites
already have reversal paths that are the most brittle code in the economy
(TESTING_PLAN §3 names the ATM's reversal specifically). Adding a veto that can
fire in the middle of them buys a capability nobody has asked for, at the cost of
touching the code most likely to lose money when it goes wrong.

A consumer that wants to prevent a purchase can refuse at its own call site,
which is where it has the context to do so anyway. If a real need appears, the
`guard` choke point in `EconomyHandleImpl` is where it goes, and it can be added
without an `API_VERSION` bump.

#### Why the local transaction ledger is deferred

Still worth having, still cheap to add — the Phase 3 choke point and the Phase 4
events mean the hooks already exist. It is deferred because it is its own feature
with its own storage, retention and query questions (how long, how big, who can
read it, does it survive a world copy), and none of those are economy-API
questions. Bolting a half-considered ledger onto this work would make both worse.

---

## 7. Risks and brittle areas

| Risk | Mitigation |
|---|---|
| `apiPackage` changes the published artifact set in ways the buildscript docs don't spell out | Phase 1 verifies `build/libs/` contents explicitly before anything depends on it |
| A consumer calls off-thread and corrupts inventory | Thread guard on every mutation, refuse rather than proceed |
| Bank callback dropped on a guard rejection → consumer hangs forever | Explicit "callback fires exactly once on every path" test |
| Escrow closes a ticket whose credit failed → money vanishes | Ticket stays open unless the credit succeeded |
| Escrow release to an offline player | `RECIPIENT_OFFLINE`, ticket stays open, documented limitation |
| Remote backend rounds differently than the wallet | `quantiseForBank` is public and the docs require calling it before both sides move |
| Event listener throws mid-transaction | Posts are wrapped; subscriber exceptions logged, never propagated |
| `WalletService.spend` rollback double-posts events | Resolved explicitly in Phase 4, not left ambiguous |
| Allowlist read as a security guarantee | Stated plainly in the docs and in the config comment |
| Remote-backend behaviour diverging from local | Phase 8 runs the whole suite twice, once per backend |

Existing brittle areas this work touches, from TESTING_PLAN §3: `WalletService.spend`'s
bill-breaking, and the ATM's reversal path in `AtmPacketTransaction`.

---

## 8. Files touched

**New:** everything under `api/` and `economy/apiimpl/` (§4), plus
`docs/SUM_ECONOMY_API.md`.

**Modified:**

| File | Change |
|---|---|
| `buildscript.properties` | `apiPackage = api` |
| `addon.gradle` | wire `apiJar` into `build`; API isolation guard |
| `.github/workflows/build-mod-release-pre-release-main.yml` | publish the api jar on releases and pre-releases |
| `.github/workflows/test-mod-build-pr.yml` | assert the api jar builds; upload as a PR artifact |
| `SumConfig.java` | `economy_integration` category, parser, getters |
| `Sum.java` | install/uninstall the API registry; register the escrow orphan sweep |
| `economy/WalletService.java` | post events on success |
| `bank/BankService.java` | post events; metadata overload for deposit/withdraw |
| `economy/MoneyTransfer.java` | delegate `roundToCents` to the new `economy/MoneyMath.java` |
| `omceapi/OmceProtocol.java` | `TX_MOD_DEPOSIT` / `TX_MOD_WITHDRAW` |
| `command/CommandSum.java` | `/sum econ api …` subcommands |
| `omceapi/service/OmceEconomyService.java` | warn when `strictTransactionTypes` meets an authorized `bank_write` |
| `docs/examples/example_config.cfg` | new category |
| `docs/agent_progress/TESTING_PLAN.md` | §1, §2, §4, §8 updates |
| `CLAUDE.md` | architecture + source layout |

---

## 9. Progress log

Append one row per session. Keep it terse.

| Date | Phase | What happened | Commit |
|---|---|---|---|
| 2026-08-09 | — | Plan written; four design decisions locked with Alex | — |
| 2026-08-09 | 1 | API surface complete (9 types), `apiJar` wired into `build`, isolation guard added, 22 tests, api jar published by both workflows | see below |
| 2026-08-09 | 2 | `economy_integration` config category, allowlist parser, authorizer + registry, server lifecycle wiring, 11 tests. Verified on a real dedicated server boot: grant, scope expansion, case tolerance, malformed-line skip, unknown scope, duplicate mod id | see below |
| 2026-08-09 | 3 | Wallet + bank wired through the guard; `MoneyMath` extracted; `BankService` gained `failureCode` and metadata/type overloads; `TX_MOD_*` added; 17 tests (477 total). Server boots clean | see below |
| 2026-08-09 | 6 | `/sum econ api mods\|escrow\|refund\|reload`; `EscrowService` admin methods | see below |
| 2026-08-09 | 4 | Event types + `EconomyAttribution` + `EconomyEventPoster`; posts wired into wallet, bank and escrow; 5 tests. Fixed remote-bank attribution across the async boundary | see below |
| 2026-08-09 | 7 | `docs/SUM_ECONOMY_API.md` written; every code sample verified to compile | see below |
| 2026-08-09 | 8 | External probe mod compiled against the api jar ALONE and booted on a dedicated server: 22/22 startup checks pass. In-game money movement still unverified — `/casinoprobe` built for it | see below |
| 2026-08-09 | 9 | TESTING_PLAN §1.2/1.5/1.10/2/4.14 and CLAUDE.md updated | see below |
| 2026-08-09 | 5 | Escrow: `EscrowSavedData`, `EscrowService`, `EscrowEvents`, handle wiring, idling orphan sweep; 16 tests (493 total). Event posting deferred to Phase 4. Not yet exercised in game | see below |
| 2026-08-11 | 8 | **Money moved.** `/casinoprobe` passed 32/32 in a dev client on the local backend: wallet credit/spend, escrow open → release/refund/forfeit, double-settlement refused, and a forged $1,000,000 ticket paying out the $10 really held. 19 events observed with correct attribution. Fixed the audit log rendering settled holds as `0.0 for unknown` | *(follows)* |
| 2026-08-11 | 5, 9 | Review pass. Found and closed the `escrowForfeit` gap — a casino could not take a losing wager, and the documented slot machine refunded every loss. Shipped the strict-transaction-types warning; answered all five §10 open questions; dropped cancellable `Pre` events and deferred the ledger, both with reasons. 5 tests (503 total); server re-verified 24/24. The whole feature committed as eight commits beginning at `726ba9b` | `726ba9b`+ |

---

## 10. Open questions — all answered as of 2026-08-11

- [x] **Phase 3.** Should `maxWalletTransaction` apply to credits as well as
      spends? — **Yes, and it does.** `walletCredit` passes `walletLimit()` to
      the same guard `walletSpend` does. Credits are the more dangerous
      direction: a spend capped too low annoys a player, whereas an uncapped
      credit is a buggy consumer minting money into a live economy.
- [x] **Phase 5.** Should escrow be able to hold *bank* money, or wallet only? —
      **Wallet only**, as built. Bank escrow means an asynchronous open, so a
      ticket would have a pending state that can fail after the caller has
      already been told the wager is on. Every extra state here is a state in
      which real money can be stranded. Revisit only if something concrete needs
      it.
- [x] **Phase 5.** Where does house-edge money go? — **Destroyed, via
      `escrowForfeit`**, matching the server shop's precedent. See the Phase 5
      note dated 2026-08-11 for why it is a separate method rather than a release
      with a null recipient.
- [x] **Phase 7.** Maven repo or the GitHub Releases Ivy pattern? — **GitHub
      Releases**, which is what the release workflow already publishes the api
      jar to, and the same pattern SUM itself uses to consume CSM. It needs no
      new infrastructure and no credentials, and consumers can pin a tag. The one
      caveat is in the consumer doc: don't pin a **pre-release** jar, since those
      are deleted after 90 days.
- [x] **Phase 6 or 7.** Warn when `strictTransactionTypes` meets `bank_write`? —
      **Yes, shipped 2026-08-11.** `OmceEconomyService.warnAboutFallbacks` now
      follows its generic strict-mode warning with a second line naming the mods
      holding `bank_write` and the exact two types (`mod_deposit`,
      `mod_withdraw`) the service must accept. The generic warning was easy to
      shrug off; the failure it predicts otherwise surfaces as one player's bank
      move failing, long after anyone would connect it to a capability flag.

---

## 10a. What Phase 8 actually proved, and what it didn't

**Proved, by an external mod compiled against the `-api` jar alone and booted on
a dedicated server (24/24 checks, re-run 2026-08-11):** the api jar is genuinely
self-contained; deny-by-default; grant with correct scope expansion (`escrow` →
`wallet_write` → `wallet_read`) and no over-granting; status reporting; guard
rejections for null player, negative and `NaN` amounts, and missing scope; that a
bank callback fires **exactly once** even on immediate rejection; that balances
are never `NaN`; that `quantiseForBank` rounds down; and that both documented
code samples compile.

**Proved on 2026-08-11, in a dev client, with real money:** `/casinoprobe` passed
32/32 on the local backend. Wallet credit and spend move exact amounts; an
overspend is refused and changes nothing; escrow open → release, → refund and →
forfeit each conserve or destroy exactly the stored amount; a second settlement
of the same ticket is refused and pays nothing; **a ticket forged to claim
$1,000,000 paid out the $10 that was really held**; and the wallet ends exactly
where it started. Events fired for all 19 movements with the right types and
`mycasino` attribution.

**Still not proved:** the whole remote-backend story, crash-safety of a hold
across a restart, the orphan sweep, a *successful* bank deposit/withdraw, and
that SUM's own transactions post events attributed to `sum`. TESTING_PLAN §4.14
is the checklist.

The original distinction is still worth keeping. "503 unit tests pass and a
consumer mod loads" was a real result, and it was *not* the same as "the economy
works" — which is now known, rather than assumed, for one of the two backends.

**And a worked example of exactly that gap:** the `escrowForfeit` hole (Phase 5,
2026-08-11) survived every one of those checks. The api jar was self-contained,
the consumer compiled, the tests were green, and the flagship documented example
still gave every losing wager back to the player. Nothing automated here was ever
going to catch it, because the failure was in what the API *offered*, not in what
it did. Passing checks constrain how wrong something can be; they do not make it
right.

---

## 11. Definition of done

- [x] `./gradlew build` green; `./gradlew test` green with new tests included.
      *(503 tests, 0 failures, 2026-08-11.)*
- [x] The `-api` jar exists and contains only api classes. *(17 classes, 0
      leaks; the `addon.gradle` guard fails the build on an internal import.)*
- [x] An unauthorized mod is denied; an authorized one gets exactly its scopes.
- [x] Wallet and escrow work on the **local** backend. *(32/32 in game,
      2026-08-11. Bank is covered there only as a correct scope refusal.)*
- [ ] Wallet, bank and escrow all work on the **remote** OMCE backend.
- [ ] Events fire for both SUM-internal and third-party transactions.
- [ ] Escrow survives a server restart and never duplicates or loses value.
- [x] `docs/SUM_ECONOMY_API.md` complete, samples verified to compile.
- [x] TESTING_PLAN.md and CLAUDE.md updated.
- [x] No behavioural change to SUM when `allowedMods` is empty. *(Deny-by-default
      is the parser's empty case; the registry installs but grants nothing, and
      the escrow sweep idles after one pass with no tickets.)*

The unticked boxes are now all about the **remote** backend, plus the three
local scenarios the probe cannot script: a restart mid-hold, the orphan sweep,
and SUM's own transactions posting events. See §10a.

---

## 12. Resume prompt

Paste this into a fresh session to pick the work up cold.

```
Continue the SUM Economy API work.

Phases 1-7 and 9 are complete. Phase 8 is verified on the LOCAL backend
(/casinoprobe passed 32/32 in game on 2026-08-11) and outstanding on the REMOTE
one. Everything is COMMITTED on dev/ogh and not pushed. Read
docs/agent-plans/ECONOMY_API_PLAN.md first — locked decisions in §2, rejected
alternatives in §3, architecture in §4, per-phase detail in §6, what Phase 8 did
and did not prove in §10a, and the progress log in §9.

The single most important outstanding item: THE REMOTE OMCE BACKEND HAS NEVER
BEEN EXERCISED. Everything proved so far ran on the local backend, so the claim
"works with both setups" is still half unverified, and it is the half most
likely to be quietly false — watch quantisation on a whole-unit currency and the
ambiguity-recovery path. Also outstanding locally: a restart mid-hold, the
orphan sweep, a successful bank deposit/withdraw, and SUM's own transactions
posting events attributed to `sum`.

The checklist is docs/agent_progress/TESTING_PLAN.md §4.14. A probe mod is built
at run/mods/mycasino-probe.jar (authorized in run/config/sum.cfg as
`mycasino=escrow`) and registers /casinoprobe to run the whole money-movement
suite in one command; leave both in place until §4.14 is finished. Do not tag a
release before it is — a tag is what consumers pin to.

Every §10 open question is now answered and every Phase 9 decision is recorded,
so there is nothing left to decide — only to verify. If you find yourself about
to re-open one, read the answer first; it says why.

Rules for this work:
- Do not re-litigate anything in §2 (locked decisions) or §3 (rejected
  alternatives) without asking Alex first.
- Tick checkboxes in the plan as you complete them and append a row to the §9
  progress log at the end of each session.
- Match the existing code conventions: SumConfig's declaration triplets, the
  JUnit 5 package-private test style, and TESTING_PLAN §4.0's rule that
  anything needing a live Minecraft runtime gets its pure logic extracted into
  a package-private helper for testing.
- Build with: JAVA_HOME="<Java 21+ path>" ./gradlew build
- Test with:  JAVA_HOME="<Java 21+ path>" ./gradlew test
- Never git push. Commit only when asked.

Key context you will need:
- WalletService (economy/) is synchronous and always local; the wallet is the
  invisible ISumMoney capability PLUS the face value of carried bill items.
- BankService (bank/) is async on both backends, and its Consumer<Result>
  callback always fires on the server thread — that uniformity is what makes
  one API able to serve both the local and remote setups.
- EconomyBridge is the invisible-balance backend selector only. It does NOT
  route to the remote service; it just owns the remote handle.
- SUM currently fires zero economy events. Phase 4 adds the first ones.
- The allowlist is operator authorization and audit attribution, NOT a security
  boundary. Do not describe it as one anywhere.
```
