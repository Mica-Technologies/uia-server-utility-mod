package com.micatechnologies.minecraft.sum.omceapi;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A committed transaction together with the post-transaction balances of every settled party.
 *
 * <p>Returning both in one object mirrors the protocol: {@code /processTransaction} is a single
 * round trip that both moves the money and refreshes the caller's view of it, so a purchase never
 * needs a follow-up balance read.
 */
public final class OmceTransactionResult {

    private final OmceTransaction transaction;
    private final List<OmceBalance> balances;

    public OmceTransactionResult(OmceTransaction transaction, @Nullable List<OmceBalance> balances) {
        this.transaction = transaction;
        this.balances = balances == null ? Collections.emptyList() : balances;
    }

    public OmceTransaction getTransaction() {
        return transaction;
    }

    /** Post-transaction balances, one per settled party. Two entries for a player-to-player pay. */
    public List<OmceBalance> getBalances() {
        return balances;
    }

    /** @return the balance for the given player, or null if the response didn't carry one. */
    @Nullable
    public OmceBalance balanceFor(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        for (OmceBalance b : balances) {
            if (playerUuid.equals(b.getPlayerUuid())) {
                return b;
            }
        }
        return null;
    }

    public boolean isCommitted() {
        return transaction.isCommitted();
    }

    @Override
    public String toString() {
        return "OmceTransactionResult{" + transaction + ", balances=" + balances.size() + "}";
    }
}
