package com.micatechnologies.minecraft.sum.api;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A single capability an integrating mod can be granted over SUM's economy.
 *
 * <p>Scopes are granted per mod in the server's {@code economy_integration.allowedMods} config,
 * using the {@link #getToken() token} spelling — for example
 * {@code casino=wallet_read,wallet_write,escrow}. A mod that holds an
 * {@link EconomyHandle} may only call the methods its scopes cover; anything else fails with
 * {@link EconomyFailure#MISSING_SCOPE} and moves no money.
 *
 * <p><b>Some scopes imply others</b>, because the implied one is meaningless to withhold. A mod
 * that can spend a wallet can trivially discover the balance by attempting a spend, so granting
 * {@link #WALLET_WRITE} without {@link #WALLET_READ} would buy nothing and only produce confusing
 * failures. {@link #expand} applies those rules; the authorization layer always stores expanded
 * sets so a scope check is a plain {@code contains}.
 */
public enum EconomyScope {

    /** Read a player's wallet balance and test affordability. */
    WALLET_READ("wallet_read"),

    /** Spend from and credit to a player's wallet. Implies {@link #WALLET_READ}. */
    WALLET_WRITE("wallet_write"),

    /** Read a player's bank balance. */
    BANK_READ("bank_read"),

    /** Deposit to and withdraw from a player's bank account. Implies {@link #BANK_READ}. */
    BANK_WRITE("bank_write"),

    /**
     * Hold wallet money in escrow and later release or refund it. Implies {@link #WALLET_WRITE},
     * since opening a ticket debits the wallet and refunding one credits it.
     */
    ESCROW("escrow");

    /** Config token granting every scope: {@code somemod=*}. */
    public static final String WILDCARD_TOKEN = "*";

    private final String token;

    EconomyScope(String token) {
        this.token = token;
    }

    /** The spelling used in the {@code allowedMods} config list. */
    public String getToken() {
        return token;
    }

    /**
     * Resolves a config token to a scope.
     *
     * @return the scope, or null if the token is not recognised. Callers should warn and skip
     *     rather than fail — an operator's typo should not take the whole allowlist down.
     */
    @Nullable
    public static EconomyScope fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalised = token.trim().toLowerCase(Locale.ROOT);
        for (EconomyScope scope : values()) {
            if (scope.token.equals(normalised)) {
                return scope;
            }
        }
        return null;
    }

    /** Every scope, as granted by {@link #WILDCARD_TOKEN}. */
    public static Set<EconomyScope> all() {
        return Collections.unmodifiableSet(EnumSet.allOf(EconomyScope.class));
    }

    /**
     * Closes a set of granted scopes under the implication rules described on this enum.
     *
     * <p>Applied repeatedly until nothing new is added, so a chain like
     * {@code ESCROW -> WALLET_WRITE -> WALLET_READ} resolves fully in one call regardless of how
     * long the chain grows later.
     *
     * @return an unmodifiable set; never null, empty for null or empty input.
     */
    public static Set<EconomyScope> expand(@Nullable Collection<EconomyScope> granted) {
        EnumSet<EconomyScope> result = EnumSet.noneOf(EconomyScope.class);
        if (granted == null || granted.isEmpty()) {
            return Collections.unmodifiableSet(result);
        }
        for (EconomyScope scope : granted) {
            if (scope != null) {
                result.add(scope);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (EconomyScope scope : EnumSet.copyOf(result)) {
                changed |= result.addAll(scope.directlyImplied());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** One step of the implication graph. {@link #expand} takes the transitive closure. */
    private Set<EconomyScope> directlyImplied() {
        switch (this) {
            case WALLET_WRITE:
                return EnumSet.of(WALLET_READ);
            case BANK_WRITE:
                return EnumSet.of(BANK_READ);
            case ESCROW:
                return EnumSet.of(WALLET_WRITE);
            default:
                return EnumSet.noneOf(EconomyScope.class);
        }
    }
}
