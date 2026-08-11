package com.micatechnologies.minecraft.sum.api;

/**
 * A snapshot of what the economy can currently do.
 *
 * <p>Read this before building a GUI or a price list: it tells you the currency symbol to render,
 * how many decimal places the bank can actually store, and whether the bank is remote — which is
 * the only case where a deposit can be refused after the fact.
 *
 * <p>Immutable and cheap to obtain, but not cached: a remote bank can go degraded and come back
 * within a session, so re-read rather than holding one at startup.
 */
public final class EconomyStatus {

    private final boolean available;
    private final boolean remoteBank;
    private final boolean bankDegraded;
    private final String currencySymbol;
    private final int bankMinorUnitDigits;

    private EconomyStatus(boolean available, boolean remoteBank, boolean bankDegraded,
            String currencySymbol, int bankMinorUnitDigits) {
        this.available = available;
        this.remoteBank = remoteBank;
        this.bankDegraded = bankDegraded;
        this.currencySymbol = currencySymbol;
        this.bankMinorUnitDigits = bankMinorUnitDigits;
    }

    /** No economy backend is attached — every mutating call will fail. */
    public static EconomyStatus unavailable() {
        return new EconomyStatus(false, false, false, "$", 2);
    }

    /** A working economy. */
    public static EconomyStatus available(boolean remoteBank, boolean bankDegraded,
            String currencySymbol, int bankMinorUnitDigits) {
        return new EconomyStatus(true, remoteBank, bankDegraded,
                currencySymbol != null ? currencySymbol : "$", bankMinorUnitDigits);
    }

    /** False when no backend is attached. Wallet reads return empty and mutations fail. */
    public boolean isAvailable() {
        return available;
    }

    /**
     * True when a remote service owns bank accounts, false when they live in the world save.
     *
     * <p>Consumers should not branch on this to decide *what* to call — the API behaves the same
     * either way. It is here for wording ("your bank is temporarily unreachable") and for deciding
     * how much to trust a cached balance.
     */
    public boolean isRemoteBank() {
        return remoteBank;
    }

    /**
     * True when the remote bank is reachable but unhealthy. Bank operations may fail; wallet
     * operations are unaffected, because the wallet is always local.
     */
    public boolean isBankDegraded() {
        return bankDegraded;
    }

    /** The symbol to render in front of an amount, e.g. {@code "$"}. */
    public String getCurrencySymbol() {
        return currencySymbol;
    }

    /**
     * Decimal places the bank can store exactly. The wallet always handles cents; a remote bank may
     * be coarser, even whole units. Pass amounts through
     * {@link EconomyHandle#quantiseForBank(double)} before moving money into or out of the bank.
     */
    public int getBankMinorUnitDigits() {
        return bankMinorUnitDigits;
    }

    /** The API version SUM is serving. Same value as {@link SumEconomy#API_VERSION}. */
    public int getApiVersion() {
        return SumEconomy.API_VERSION;
    }

    @Override
    public String toString() {
        if (!available) {
            return "EconomyStatus[unavailable]";
        }
        return "EconomyStatus[available, bank=" + (remoteBank ? "remote" : "local")
                + (bankDegraded ? " (degraded)" : "") + ", symbol=" + currencySymbol
                + ", digits=" + bankMinorUnitDigits + "]";
    }
}
