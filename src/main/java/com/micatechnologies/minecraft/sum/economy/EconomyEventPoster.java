package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import com.micatechnologies.minecraft.sum.api.event.BankTransactionEvent;
import com.micatechnologies.minecraft.sum.api.event.EscrowEvent;
import com.micatechnologies.minecraft.sum.api.event.SumEconomyEvent;
import com.micatechnologies.minecraft.sum.api.event.WalletTransactionEvent;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * Posts SUM's economy events, and stops a listener from doing damage while it does.
 *
 * <p>Every post happens <b>after</b> the money has already moved, so there is nothing left to
 * abort. A subscriber that throws is therefore logged and swallowed: letting the exception
 * propagate would unwind a caller that has already committed a transaction, turning somebody
 * else's broken listener into SUM's corrupted balance.
 *
 * <p>Attribution comes from {@link EconomyAttribution}, so a movement an integrating mod caused
 * carries that mod's id even though it went through the same code as a shop purchase.
 */
public final class EconomyEventPoster {

    private EconomyEventPoster() {}

    /** Money left or entered a wallet. */
    public static void walletMoved(EntityPlayer player, WalletTransactionEvent.Type type,
            double amount, double resultingBalance) {
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        post(new WalletTransactionEvent(player, EconomyAttribution.currentModId(), type, amount,
            EconomyAttribution.currentReason(), resultingBalance));
    }

    /** Money settled into or out of a bank account. Never posted for a failed request. */
    public static void bankMoved(EntityPlayer player, BankTransactionEvent.Type type, double amount,
            double resultingBalance, boolean remoteBackend, @Nullable String reason) {
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        post(new BankTransactionEvent(player, EconomyAttribution.currentModId(), type, amount,
            reason != null ? reason : EconomyAttribution.currentReason(), resultingBalance,
            remoteBackend));
    }

    /** A hold was created, settled, refunded, or swept up after its mod vanished. */
    public static void escrowChanged(EntityPlayer player, EscrowEvent.Type type,
            EscrowTicket ticket, @Nullable String reason) {
        if (player == null || ticket == null || player.world == null || player.world.isRemote) {
            return;
        }
        post(new EscrowEvent(player, ticket.getOwningModId(), type, ticket, reason));
    }

    private static void post(SumEconomyEvent event) {
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } catch (Throwable t) {
            Sum.LOGGER.error("[economy] A listener threw while handling {}. The transaction itself "
                + "already completed and is unaffected.", event.getClass().getSimpleName(), t);
        }
    }
}
