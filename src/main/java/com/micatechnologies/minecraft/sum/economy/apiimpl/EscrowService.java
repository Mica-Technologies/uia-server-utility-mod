package com.micatechnologies.minecraft.sum.economy.apiimpl;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.api.EconomyFailure;
import com.micatechnologies.minecraft.sum.api.EconomyResult;
import com.micatechnologies.minecraft.sum.api.EscrowResult;
import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import com.micatechnologies.minecraft.sum.api.event.EscrowEvent;
import com.micatechnologies.minecraft.sum.economy.EconomyEventPoster;
import com.micatechnologies.minecraft.sum.economy.MoneyMath;
import com.micatechnologies.minecraft.sum.economy.WalletService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;

/**
 * Holds wallet money between a commitment and its resolution.
 *
 * <p>This is the primitive behind a wager. A stake leaves the player's wallet the moment they
 * commit it, so it cannot be spent twice while a round plays out, and it is written to the world
 * save so a crash mid-round does not delete it.
 *
 * <p><b>Escrow conserves value; it never creates it.</b> Releasing a ticket moves exactly what was
 * held. A payout larger than the stake is a release plus a separate
 * {@link com.micatechnologies.minecraft.sum.api.EconomyHandle#walletCredit walletCredit}, which is
 * subject to the operator's credit cap. There is deliberately no path here that pays out more than
 * came in.
 *
 * <p><b>Tickets handed in by a caller are never trusted.</b>
 * {@link EscrowTicket} is a public value type with a public constructor, so a buggy or hostile
 * consumer can fabricate one claiming any amount it likes. Every operation therefore looks the
 * ticket up by id in the world save and uses the <i>stored</i> amount and owner; the object passed
 * in supplies nothing but an id.
 *
 * <p>Server thread only. Callers come through {@link EconomyHandleImpl}, which has already checked
 * scope, side, thread and amount.
 */
public final class EscrowService {

    /** How many recently closed ticket ids to remember, purely to give a better error message. */
    private static final int CLOSED_MEMORY = 256;

    /** Ticks between orphan-sweep attempts. Five seconds; the sweep is idle most of the time. */
    private static final int SWEEP_INTERVAL_TICKS = 100;

    /**
     * Ids closed this session, so releasing the same ticket twice can say "already settled"
     * rather than "no such ticket". In memory only — after a restart a double-release reports
     * the vaguer error, but it still refuses and still moves no money, which is what matters.
     */
    private static final Map<UUID, Boolean> recentlyClosed =
        Collections.synchronizedMap(new LinkedHashMap<UUID, Boolean>(CLOSED_MEMORY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                return size() > CLOSED_MEMORY;
            }
        });

    /** Epoch millis after which the orphan sweep may run. Set when the server starts. */
    private static volatile long sweepDueAtMillis = Long.MAX_VALUE;

    /** False once a sweep finds nothing left to do, so idle servers stop scanning every 5s. */
    private static volatile boolean sweepPending = false;

    private static int tickCounter = 0;

    private EscrowService() {}

    // -------------------------------------------------------------------------------------------
    // Operations
    // -------------------------------------------------------------------------------------------

    /** Debits the player's wallet and records a hold against a new ticket. */
    static EscrowResult open(String modId, EntityPlayer player, double amount, String reason) {
        EscrowSavedData data = data();
        if (data == null) {
            return EscrowResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE,
                "The world is not available to hold money right now.");
        }
        if (!WalletService.isAvailable()) {
            return EscrowResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE);
        }
        double rounded = MoneyMath.roundToCents(amount);
        if (rounded <= 0.0) {
            return EscrowResult.fail(EconomyFailure.INVALID_AMOUNT,
                "An amount must be at least one cent to be held.");
        }
        if (!WalletService.spend(player, rounded)) {
            return EscrowResult.fail(EconomyFailure.INSUFFICIENT_FUNDS);
        }

        // Past this point the wallet has already been debited, so the ticket must be recorded.
        EscrowTicket ticket = new EscrowTicket(UUID.randomUUID(), player.getUniqueID(), rounded,
            modId, EconomyHandleImpl.attributeReason(modId, reason), System.currentTimeMillis());
        data.put(ticket);
        logIfEnabled("escrowOpen", modId, player.getName(), rounded, ticket.getId(), "held");
        EconomyEventPoster.escrowChanged(player, EscrowEvent.Type.OPENED, ticket, reason);
        return EscrowResult.ok(ticket);
    }

    /** Closes a hold and credits it to {@code recipient}. */
    static EconomyResult release(String modId, @Nullable EscrowTicket ticket,
            @Nullable EntityPlayer recipient, String reason) {
        return close(modId, ticket, recipient, reason, "escrowRelease");
    }

    /** Closes a hold and returns it to whoever put it up. */
    static EconomyResult refund(String modId, @Nullable EscrowTicket ticket, String reason) {
        Lookup lookup = lookup(modId, ticket);
        if (lookup.failure != null) {
            return lookup.failure;
        }
        EntityPlayer owner = onlinePlayer(lookup.ticket.getOwner());
        if (owner == null) {
            return EconomyResult.fail(EconomyFailure.RECIPIENT_OFFLINE,
                "The player who put that money up is not online, so it stays held.");
        }
        return close(modId, ticket, owner, reason, "escrowRefund");
    }

    /**
     * Closes a hold and destroys the money it held — the losing side of a wager.
     *
     * <p>The only path here that removes value from the economy. It deliberately needs nobody
     * online: a round has to be settleable after the player logs out, and refusing would leave the
     * hold sitting until the orphan sweep eventually handed the stake back, turning every
     * disconnect into a free bet.
     *
     * <p>Because every economy event names a player, no event is posted when the owner is offline.
     * The money is destroyed either way — the event is a notification, not the transaction.
     */
    static EconomyResult forfeit(String modId, @Nullable EscrowTicket ticket, String reason) {
        Lookup lookup = lookup(modId, ticket);
        if (lookup.failure != null) {
            return lookup.failure;
        }
        EscrowTicket stored = lookup.ticket;
        EscrowSavedData data = data();
        if (data == null) {
            return EconomyResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE);
        }
        EntityPlayer owner = onlinePlayer(stored.getOwner());

        // Nothing to credit, so unlike close() there is no failure that can strand the ticket:
        // removing it *is* the destruction.
        data.remove(stored.getId());
        recentlyClosed.put(stored.getId(), Boolean.TRUE);
        logIfEnabled("escrowForfeit", modId, owner != null ? owner.getName() : "an offline player",
            stored.getAmount(), stored.getId(),
            "destroyed: " + (reason == null ? "no reason given" : reason));
        if (owner != null) {
            EconomyEventPoster.escrowChanged(owner, EscrowEvent.Type.FORFEITED, stored, reason);
        }
        // NaN is SUM's "unknown", which EconomyResult reports as an unknown balance rather than
        // inventing a figure for a player who is not here to have one read.
        return EconomyResult.ok(owner != null ? WalletService.getTotal(owner) : Double.NaN);
    }

    /** Open holds belonging to one mod. Never exposes another mod's tickets. */
    static List<EscrowTicket> listOpen(String modId) {
        EscrowSavedData data = data();
        return data == null ? Collections.emptyList() : data.listFor(modId);
    }

    // -------------------------------------------------------------------------------------------
    // Operator tooling — reached from /sum econ api, not from the public API.
    // -------------------------------------------------------------------------------------------

    /** Every open hold on the server, oldest first. For {@code /sum econ api escrow}. */
    public static List<EscrowTicket> adminListAll() {
        EscrowSavedData data = data();
        return data == null ? Collections.emptyList() : data.listAll();
    }

    /**
     * Refunds a hold on an operator's say-so, ignoring which mod owns it.
     *
     * <p>The escape hatch for a hold that nothing will ever settle: a mod that lost track of a
     * ticket, or one whose owner is still authorized so the orphan sweep leaves it alone. It still
     * refuses to close a ticket whose credit did not land, for the same reason the normal path
     * does.
     *
     * @return null on success, or a message explaining why it could not be refunded.
     */
    @Nullable
    public static String adminForceRefund(UUID ticketId) {
        EscrowSavedData data = data();
        if (data == null) {
            return "The world is not loaded.";
        }
        EscrowTicket ticket = data.getTicket(ticketId);
        if (ticket == null) {
            return recentlyClosed.containsKey(ticketId)
                ? "That hold was already settled."
                : "There is no open hold with that id.";
        }
        EntityPlayer owner = onlinePlayer(ticket.getOwner());
        if (owner == null) {
            return "The player who put that money up is offline. They must be online to be paid.";
        }
        if (!WalletService.credit(owner, ticket.getAmount())) {
            return "The wallet could not be credited, so the hold is still open.";
        }
        data.remove(ticket.getId());
        recentlyClosed.put(ticket.getId(), Boolean.TRUE);
        EconomyEventPoster.escrowChanged(owner, EscrowEvent.Type.REFUNDED, ticket,
            "refunded by an operator");
        Sum.LOGGER.warn("[economy-api] operator force-refunded ${} to {} from hold {} owned by "
            + "'{}'.", ticket.getAmount(), owner.getName(), ticket.getId(),
            ticket.getOwningModId());
        return null;
    }

    /**
     * Shared close path for release and refund.
     *
     * <p>The ticket is removed only after the credit lands. If crediting fails the hold stays
     * open and the caller gets an error, because a closed ticket whose money never arrived is
     * money deleted — the one outcome escrow exists to prevent.
     */
    private static EconomyResult close(String modId, @Nullable EscrowTicket ticket,
            @Nullable EntityPlayer recipient, String reason, String operation) {
        Lookup lookup = lookup(modId, ticket);
        if (lookup.failure != null) {
            return lookup.failure;
        }
        EscrowTicket stored = lookup.ticket;
        if (recipient == null || onlinePlayer(recipient.getUniqueID()) == null) {
            return EconomyResult.fail(EconomyFailure.RECIPIENT_OFFLINE);
        }
        if (!WalletService.credit(recipient, stored.getAmount())) {
            Sum.LOGGER.error("[economy-api] '{}' could not credit {} with the ${} held in ticket "
                + "{}; the hold stays open so the money is not lost.", modId, recipient.getName(),
                stored.getAmount(), stored.getId());
            return EconomyResult.fail(EconomyFailure.BACKEND_REFUSED,
                "The money could not be paid out, so it is still held.");
        }
        EscrowSavedData data = data();
        if (data != null) {
            data.remove(stored.getId());
        }
        recentlyClosed.put(stored.getId(), Boolean.TRUE);
        logIfEnabled(operation, modId, recipient.getName(), stored.getAmount(), stored.getId(),
            reason == null ? "settled" : reason);
        EconomyEventPoster.escrowChanged(recipient,
            "escrowRefund".equals(operation) ? EscrowEvent.Type.REFUNDED
                : EscrowEvent.Type.RELEASED,
            stored, reason);
        return EconomyResult.ok(WalletService.getTotal(recipient));
    }

    /** Outcome of resolving a caller-supplied ticket against the world save. */
    private static final class Lookup {
        @Nullable final EscrowTicket ticket;
        @Nullable final EconomyResult failure;

        Lookup(@Nullable EscrowTicket ticket, @Nullable EconomyResult failure) {
            this.ticket = ticket;
            this.failure = failure;
        }
    }

    /**
     * Resolves a caller's ticket to the stored one, refusing anything that is not this mod's open
     * hold.
     *
     * <p>A ticket belonging to another mod reports {@code ESCROW_NOT_FOUND} rather than a
     * permission error: whether some other mod happens to hold a ticket is not this caller's
     * business, and saying so would let one integration probe another's state.
     */
    private static Lookup lookup(String modId, @Nullable EscrowTicket ticket) {
        if (ticket == null || ticket.getId() == null) {
            return new Lookup(null, EconomyResult.fail(EconomyFailure.ESCROW_NOT_FOUND,
                "No escrow ticket was given."));
        }
        EscrowSavedData data = data();
        if (data == null) {
            return new Lookup(null, EconomyResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE));
        }
        EscrowTicket stored = data.getTicket(ticket.getId());
        if (stored == null) {
            boolean closed = recentlyClosed.containsKey(ticket.getId());
            return new Lookup(null, EconomyResult.fail(
                closed ? EconomyFailure.ESCROW_ALREADY_CLOSED : EconomyFailure.ESCROW_NOT_FOUND));
        }
        if (!stored.getOwningModId().equals(modId)) {
            return new Lookup(null, EconomyResult.fail(EconomyFailure.ESCROW_NOT_FOUND));
        }
        return new Lookup(stored, null);
    }

    // -------------------------------------------------------------------------------------------
    // Orphan sweep
    // -------------------------------------------------------------------------------------------

    /** Arms the orphan sweep. Called when the economy API comes up with the server. */
    static void onServerStart() {
        long graceMillis = SumConfig.getEconomyIntegrationOrphanedEscrowGraceMinutes() * 60_000L;
        sweepDueAtMillis = System.currentTimeMillis() + graceMillis;
        sweepPending = true;
        tickCounter = 0;
        recentlyClosed.clear();
    }

    /** Disarms the sweep when the server stops. */
    static void onServerStop() {
        sweepDueAtMillis = Long.MAX_VALUE;
        sweepPending = false;
        recentlyClosed.clear();
    }

    /** Re-arms the sweep after authorization changes, since a mod may have just been revoked. */
    static void onConfigReloaded() {
        sweepPending = true;
    }

    /** Retries any hold that could not be refunded because its owner was offline. */
    public static void onPlayerLogin() {
        sweepPending = true;
    }

    /** Drives the sweep. Cheap: a counter compare on most ticks, and idle once nothing is left. */
    public static void onServerTick() {
        if (!sweepPending || ++tickCounter < SWEEP_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (System.currentTimeMillis() < sweepDueAtMillis) {
            return;
        }
        sweepOrphans();
    }

    /**
     * Refunds holds belonging to mods that are gone or no longer authorized.
     *
     * <p>A removed mod's players would otherwise never see their stakes again: nothing else in the
     * game knows those tickets exist, and the money is already out of their wallets.
     */
    static void sweepOrphans() {
        EscrowSavedData data = data();
        if (data == null || data.size() == 0) {
            sweepPending = false;
            return;
        }
        List<EscrowTicket> orphans = selectOrphans(data.listAll(), activeModIds(data.listAll()),
            System.currentTimeMillis(), sweepDueAtMillis);
        if (orphans.isEmpty()) {
            sweepPending = false;
            return;
        }
        if (!SumConfig.isEconomyIntegrationRefundOrphanedEscrow()) {
            Sum.LOGGER.warn("[economy-api] {} escrow hold(s) belong to mods that are gone or "
                + "de-authorized, but refundOrphanedEscrow is off, so they stay held. Use "
                + "'/sum econ api escrow' to inspect them.", orphans.size());
            sweepPending = false;
            return;
        }

        int refunded = 0;
        int deferred = 0;
        for (EscrowTicket ticket : orphans) {
            EntityPlayer owner = onlinePlayer(ticket.getOwner());
            if (owner == null) {
                deferred++;
                continue;
            }
            if (!WalletService.credit(owner, ticket.getAmount())) {
                Sum.LOGGER.error("[economy-api] could not refund orphaned hold {} (${}) to {}; it "
                    + "stays held.", ticket.getId(), ticket.getAmount(), owner.getName());
                deferred++;
                continue;
            }
            data.remove(ticket.getId());
            recentlyClosed.put(ticket.getId(), Boolean.TRUE);
            EconomyEventPoster.escrowChanged(owner, EscrowEvent.Type.ORPHAN_REFUNDED, ticket,
                "owning mod is gone or de-authorized");
            refunded++;
            Sum.LOGGER.warn("[economy-api] refunded ${} to {} from an orphaned hold left by '{}' "
                + "({}).", ticket.getAmount(), owner.getName(), ticket.getOwningModId(),
                ticket.getReason());
        }
        if (deferred == 0) {
            sweepPending = false;
        }
        if (refunded > 0 || deferred > 0) {
            Sum.LOGGER.warn("[economy-api] orphaned escrow sweep: {} refunded, {} waiting for the "
                + "player to come online.", refunded, deferred);
        }
    }

    /**
     * Which holds are orphaned: those whose owning mod is not active, once the grace period has
     * passed.
     *
     * <p>Pure, so the selection rule can be tested without a server. The grace runs from server
     * start rather than from each ticket's age, because the case it guards against is an operator
     * pulling a mod out for one restart — refunding everyone's in-flight wagers the instant that
     * happens would be worse than waiting.
     *
     * @param activeModIds mods that are both loaded and still authorized.
     * @return orphaned tickets, oldest first; empty while the grace period is still running.
     */
    static List<EscrowTicket> selectOrphans(Collection<EscrowTicket> open, Set<String> activeModIds,
            long nowMillis, long sweepDueAt) {
        if (open == null || open.isEmpty() || nowMillis < sweepDueAt) {
            return Collections.emptyList();
        }
        List<EscrowTicket> orphans = new ArrayList<>();
        for (EscrowTicket ticket : open) {
            if (ticket != null && !activeModIds.contains(ticket.getOwningModId())) {
                orphans.add(ticket);
            }
        }
        orphans.sort(Comparator.comparingLong(EscrowTicket::getOpenedAtMillis));
        return orphans;
    }

    /** Mod ids among the open holds that are both loaded and still authorized. */
    private static Set<String> activeModIds(Collection<EscrowTicket> open) {
        Set<String> active = new HashSet<>();
        for (EscrowTicket ticket : open) {
            String modId = ticket.getOwningModId();
            if (active.contains(modId)) {
                continue;
            }
            if (Loader.isModLoaded(modId) && EconomyAuthorizer.isAuthorized(modId)) {
                active.add(modId);
            }
        }
        return active;
    }

    // -------------------------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------------------------

    /** The escrow store, or null when there is no server or overworld to read it from. */
    @Nullable
    private static EscrowSavedData data() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return null;
        }
        World overworld = server.getWorld(0);
        return overworld == null ? null : EscrowSavedData.get(overworld);
    }

    /** The online player with this uuid, or null. Escrow can only pay someone who is here. */
    @Nullable
    private static EntityPlayer onlinePlayer(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(uuid);
    }

    private static void logIfEnabled(String operation, String modId, String who, double amount,
            UUID ticketId, String detail) {
        if (!SumConfig.isEconomyIntegrationLoggingEnabled()) {
            return;
        }
        Sum.LOGGER.info("[economy-api] {} {} ${} for {} (ticket {}): {}", modId, operation, amount,
            who, ticketId, detail);
    }
}
