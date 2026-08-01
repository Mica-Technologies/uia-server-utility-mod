package com.micatechnologies.minecraft.sum.jobs;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→server actions on a {@link JobListing}. The reward is escrowed (charged from the poster
 * at post time), so the action handler moves that held money: refunded to the poster on cancel,
 * paid to the worker on approval.
 *
 * <p>State machine: OPEN --accept--&gt; CLAIMED --submit--&gt; SUBMITTED --approve--&gt; (paid &amp;
 * removed). A worker may {@code abandon} a claim (back to OPEN); the poster may {@code reject} a
 * submission (back to CLAIMED) or {@code cancel} (refund &amp; remove).
 */
public class PacketJobAction implements IMessage {

    public static final int ACTION_REMOVE  = 0;   // poster/admin: cancel + refund escrow
    public static final int ACTION_ACCEPT  = 1;   // worker: claim an OPEN listing
    public static final int ACTION_ABANDON = 2;   // worker: release a claim back to OPEN
    public static final int ACTION_SUBMIT  = 3;   // worker: mark a CLAIMED listing done
    public static final int ACTION_APPROVE = 4;   // poster: pay the worker + remove
    public static final int ACTION_REJECT  = 5;   // poster: send a SUBMITTED listing back to CLAIMED

    private int action;
    private UUID listingId;

    public PacketJobAction() {}

    public PacketJobAction(int action, UUID listingId) {
        this.action = action;
        this.listingId = listingId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.listingId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeLong(listingId.getMostSignificantBits());
        buf.writeLong(listingId.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<PacketJobAction, IMessage> {

        @Override
        public IMessage onMessage(PacketJobAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketJobAction msg) {
            JobBoardSavedData data = JobBoardSavedData.get(player.world);
            long now = System.currentTimeMillis();
            JobListing l = data.getById(msg.listingId);
            if (l == null || l.isExpired(now)) {
                tell(player, TextFormatting.YELLOW, "That listing is no longer available.");
                pushBoard(player, data, now);
                return;
            }
            switch (msg.action) {
                case ACTION_REMOVE:  doCancel(player, data, l); break;
                case ACTION_ACCEPT:  doAccept(player, data, l); break;
                case ACTION_ABANDON: doAbandon(player, data, l); break;
                case ACTION_SUBMIT:  doSubmit(player, data, l); break;
                case ACTION_APPROVE: doApprove(player, data, l); break;
                case ACTION_REJECT:  doReject(player, data, l); break;
                default:
                    Sum.LOGGER.warn("[jobs] unknown action {} from {}", msg.action, player.getName());
                    return;
            }
            pushBoard(player, data, now);
        }

        private void doCancel(EntityPlayerMP player, JobBoardSavedData data, JobListing l) {
            boolean admin = player.canUseCommand(2, "sum.jobs.remove_any");
            if (!l.isPoster(player.getUniqueID()) && !admin) {
                tell(player, TextFormatting.RED, "You can only cancel your own listings.");
                return;
            }
            // Refund escrow to the poster (the one who paid). Credit only if they're online.
            MinecraftServer server = player.getServer();
            EntityPlayerMP poster = server == null ? null
                : server.getPlayerList().getPlayerByUUID(l.posterUuid);
            if (l.reward > 0.0 && poster != null) {
                // The listing held the escrow, so the refund comes from the job, not a faucet —
                // this is the credit half of the debit taken at post time.
                //
                // The listing is removed further down, taking the escrow record with it, so a
                // refused refund would destroy it. Restore the listing in that case.
                EconomyBridge.adjustBalance(poster, l.reward,
                    OmceProtocol.TX_JOB_REFUND, OmceParty.job(l.id, l.posterUuid),
                    "Job listing cancelled; escrow refunded",
                    () -> restoreListing(poster, l,
                        "The escrow refund was declined — your listing was restored."));
                tell(poster, TextFormatting.GREEN,
                    "Listing cancelled — $" + money(l.reward) + " escrow refunded.");
            } else if (l.reward > 0.0) {
                tell(player, TextFormatting.YELLOW,
                    "Poster is offline; their $" + money(l.reward) + " escrow could not be refunded now.");
            }
            // Notify a worker who was mid-job.
            notifyOther(player, l.workerUuid, TextFormatting.YELLOW,
                "A job you claimed was cancelled by the poster.");
            data.removeListing(l.id);
            if (l.isPoster(player.getUniqueID()) && poster == player) {
                // Poster cancelling their own already got the refund line above (if online).
                if (l.reward <= 0.0) tell(player, TextFormatting.GREEN, "Listing cancelled.");
            } else {
                tell(player, TextFormatting.GREEN, "Listing removed.");
            }
        }

        private void doAccept(EntityPlayerMP player, JobBoardSavedData data, JobListing l) {
            if (l.isPoster(player.getUniqueID())) {
                tell(player, TextFormatting.RED, "You can't accept your own listing.");
                return;
            }
            if (l.status != JobStatus.OPEN) {
                tell(player, TextFormatting.YELLOW, "That job has already been claimed.");
                return;
            }
            l.claim(player.getUniqueID(), player.getName());
            data.touch();
            tell(player, TextFormatting.GREEN,
                "Job accepted — $" + money(l.reward) + " on completion. Mark it done when finished.");
            notifyOther(player, l.posterUuid, TextFormatting.AQUA,
                player.getName() + " accepted your job: " + clip(l.description));
        }

        private void doAbandon(EntityPlayerMP player, JobBoardSavedData data, JobListing l) {
            if (!l.isWorker(player.getUniqueID())) {
                tell(player, TextFormatting.RED, "You haven't claimed that job.");
                return;
            }
            l.reopen();
            data.touch();
            tell(player, TextFormatting.GREEN, "Job released — it's open again.");
            notifyOther(player, l.posterUuid, TextFormatting.YELLOW,
                player.getName() + " released your job: " + clip(l.description));
        }

        private void doSubmit(EntityPlayerMP player, JobBoardSavedData data, JobListing l) {
            if (!l.isWorker(player.getUniqueID())) {
                tell(player, TextFormatting.RED, "You haven't claimed that job.");
                return;
            }
            if (l.status != JobStatus.CLAIMED) {
                tell(player, TextFormatting.YELLOW, "That job isn't awaiting submission.");
                return;
            }
            l.status = JobStatus.SUBMITTED;
            data.touch();
            tell(player, TextFormatting.GREEN, "Marked done — waiting for the poster to approve.");
            notifyOther(player, l.posterUuid, TextFormatting.AQUA,
                player.getName() + " marked your job done — approve to pay: " + clip(l.description));
        }

        private void doApprove(EntityPlayerMP player, JobBoardSavedData data, JobListing l) {
            if (!l.isPoster(player.getUniqueID())) {
                tell(player, TextFormatting.RED, "Only the poster can approve this job.");
                return;
            }
            if (l.workerUuid == null || l.status == JobStatus.OPEN) {
                tell(player, TextFormatting.YELLOW, "Nobody has claimed that job yet.");
                return;
            }
            MinecraftServer server = player.getServer();
            EntityPlayerMP worker = server == null ? null
                : server.getPlayerList().getPlayerByUUID(l.workerUuid);
            if (worker == null) {
                tell(player, TextFormatting.YELLOW,
                    l.workerName + " is offline — they must be online to receive payment.");
                return;
            }
            // Pay the escrow to the worker. The poster was already charged at post time.
            //
            // The listing is removed immediately below, taking the escrow record with it, so a
            // refused payout would leave the worker unpaid with nothing to retry against.
            if (l.reward > 0.0 && !EconomyBridge.adjustBalance(worker, l.reward,
                OmceProtocol.TX_JOB_PAYOUT, OmceParty.job(l.id, l.posterUuid),
                "Job completed for " + l.posterName,
                () -> restoreListing(player, l,
                    "The payout was declined — the job was restored for another attempt."))) {
                tell(player, TextFormatting.RED, "Payout failed — the worker could not be credited.");
                return;
            }
            data.removeListing(l.id);
            tell(player, TextFormatting.GREEN,
                "Approved — paid $" + money(l.reward) + " to " + worker.getName() + ".");
            tell(worker, TextFormatting.GREEN,
                "Your job was approved — you earned $" + money(l.reward) + "!");
        }

        private void doReject(EntityPlayerMP player, JobBoardSavedData data, JobListing l) {
            if (!l.isPoster(player.getUniqueID())) {
                tell(player, TextFormatting.RED, "Only the poster can reject this submission.");
                return;
            }
            if (l.status != JobStatus.SUBMITTED) {
                tell(player, TextFormatting.YELLOW, "That job hasn't been submitted for review.");
                return;
            }
            l.status = JobStatus.CLAIMED;
            data.touch();
            tell(player, TextFormatting.GREEN, "Sent back to the worker for changes.");
            notifyOther(player, l.workerUuid, TextFormatting.YELLOW,
                "The poster requested changes on a job you submitted: " + clip(l.description));
        }

        /** Re-push the active listings to the acting player so their open board refreshes. */
        private static void pushBoard(EntityPlayerMP player, JobBoardSavedData data, long now) {
            SumNetwork.CHANNEL.sendTo(new PacketOpenJobBoard(data.getActive(now)), player);
        }

        private static void notifyOther(EntityPlayerMP actor, UUID otherUuid,
                                        TextFormatting color, String text) {
            if (otherUuid == null || otherUuid.equals(actor.getUniqueID())) return;
            MinecraftServer server = actor.getServer();
            if (server == null) return;
            EntityPlayerMP other = server.getPlayerList().getPlayerByUUID(otherUuid);
            if (other != null) tell(other, color, "[jobs] " + text);
        }

        /**
         * Puts a listing back on the board after a refused credit, so the escrow it represents is
         * not silently destroyed.
         *
         * <p>Re-adds only if it is genuinely gone; a listing that is somehow still present means
         * the removal never happened, and duplicating it would create escrow rather than preserve
         * it.
         */
        private static void restoreListing(EntityPlayerMP notify, JobListing listing,
            String message) {
            JobBoardSavedData data = JobBoardSavedData.get(notify.world);
            if (data.getById(listing.id) != null) {
                return;
            }
            data.addListing(listing);
            tell(notify, TextFormatting.RED, message);
        }

        private static void tell(EntityPlayerMP player, TextFormatting color, String text) {
            TextComponentString tcs = new TextComponentString(text);
            tcs.getStyle().setColor(color);
            player.sendMessage(tcs);
        }

        private static String money(double v) {
            return String.format(Locale.ROOT, "%.2f", v);
        }

        private static String clip(String s) {
            return s.length() > 40 ? s.substring(0, 39) + "…" : s;
        }
    }
}
