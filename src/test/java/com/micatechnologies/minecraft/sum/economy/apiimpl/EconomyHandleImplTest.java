package com.micatechnologies.minecraft.sum.economy.apiimpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.api.EconomyFailure;
import com.micatechnologies.minecraft.sum.api.EconomyResult;
import com.micatechnologies.minecraft.sum.bank.BankService;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pure logic inside {@link EconomyHandleImpl}: how a bank outcome becomes a public result, and
 * how a transaction reason gets attributed to the mod that caused it.
 *
 * <p>Both matter after the fact rather than during. Classification is what lets an integration
 * tell "the player is broke" from "the bank is unreachable" and respond differently; attribution
 * is what lets an operator staring at an unexpected balance change find out which mod moved it.
 *
 * <p>The money-moving methods themselves need a live player, server and world, so they are covered
 * by the in-game checklist rather than here (TESTING_PLAN §4.0 pattern 2).
 */
class EconomyHandleImplTest {

    /** Builds a {@link BankService.Result}; its factories are package-private to the bank package. */
    private static BankService.Result bankResult(boolean ok, String error, double balance,
            String failureCode) throws Exception {
        Constructor<BankService.Result> ctor = BankService.Result.class
            .getDeclaredConstructor(boolean.class, String.class, double.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(ok, error, balance, failureCode);
    }

    // --- Failure classification ---

    @Test
    @DisplayName("a bank shortfall is reported as insufficient funds, not a generic refusal")
    void insufficientFundsIsClassified() {
        assertEquals(EconomyFailure.INSUFFICIENT_FUNDS,
            EconomyHandleImpl.classify(OmceProtocol.ERR_INSUFFICIENT_FUNDS));
    }

    @Test
    @DisplayName("an unrepresentable amount is distinguished from a refusal, since it is fixable")
    void amountInvalidIsClassified() {
        assertEquals(EconomyFailure.NOT_REPRESENTABLE,
            EconomyHandleImpl.classify(OmceProtocol.ERR_AMOUNT_INVALID));
    }

    @Test
    @DisplayName("being unable to ask the backend is not the same as the backend saying no")
    void transportProblemsAreBackendErrors() {
        // The difference matters to a consumer: a BACKEND_ERROR is worth retrying later, a
        // BACKEND_REFUSED is not.
        assertEquals(EconomyFailure.BACKEND_ERROR,
            EconomyHandleImpl.classify(OmceProtocol.ERR_SERVICE_UNAVAILABLE));
        assertEquals(EconomyFailure.BACKEND_ERROR,
            EconomyHandleImpl.classify(OmceProtocol.ERR_TRANSPORT));
        assertEquals(EconomyFailure.BACKEND_ERROR,
            EconomyHandleImpl.classify(OmceProtocol.ERR_NOT_CONNECTED));
        assertEquals(EconomyFailure.BACKEND_ERROR,
            EconomyHandleImpl.classify(OmceProtocol.ERR_RATE_LIMITED));
    }

    @Test
    @DisplayName("a deliberate refusal stays a refusal")
    void deliberateRefusalsAreRefusals() {
        assertEquals(EconomyFailure.BACKEND_REFUSED,
            EconomyHandleImpl.classify(OmceProtocol.ERR_ACCOUNT_FROZEN));
        assertEquals(EconomyFailure.BACKEND_REFUSED,
            EconomyHandleImpl.classify(OmceProtocol.ERR_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("an unknown or absent code degrades to a refusal rather than throwing")
    void unknownCodeDegrades() {
        // The protocol's error vocabulary is allowed to grow, so an unrecognised code must not
        // take down a transaction path.
        assertEquals(EconomyFailure.BACKEND_REFUSED, EconomyHandleImpl.classify(null));
        assertEquals(EconomyFailure.BACKEND_REFUSED,
            EconomyHandleImpl.classify("SOME_FUTURE_ERROR"));
    }

    // --- Result translation ---

    @Test
    @DisplayName("a successful bank result carries its balance through")
    void translateSuccess() throws Exception {
        EconomyResult result = EconomyHandleImpl.translate(bankResult(true, null, 250.0, null));
        assertTrue(result.isOk());
        assertEquals(250.0, result.getBalance().getAsDouble(), 0.0);
    }

    @Test
    @DisplayName("a failed bank result keeps its player-facing message and gains a reason")
    void translateFailure() throws Exception {
        EconomyResult result = EconomyHandleImpl.translate(bankResult(false,
            "Insufficient funds in your account.", 0.0, OmceProtocol.ERR_INSUFFICIENT_FUNDS));
        assertFalse(result.isOk());
        assertEquals(EconomyFailure.INSUFFICIENT_FUNDS, result.getFailure());
        assertEquals("Insufficient funds in your account.", result.getMessage(),
            "the bank already worded this for a player; don't replace it");
        assertFalse(result.getBalance().isPresent());
    }

    @Test
    @DisplayName("a NaN balance on success is reported as unknown, not leaked")
    void translateSuccessWithUnknownBalance() throws Exception {
        EconomyResult result =
            EconomyHandleImpl.translate(bankResult(true, null, Double.NaN, null));
        assertTrue(result.isOk());
        assertFalse(result.getBalance().isPresent());
    }

    // --- Reason attribution ---

    @Test
    @DisplayName("a reason is tagged with the mod that caused it")
    void reasonIsAttributed() {
        assertEquals("[mycasino] blackjack stake",
            EconomyHandleImpl.attributeReason("mycasino", "blackjack stake"));
    }

    @Test
    @DisplayName("a missing reason still produces an attributable entry")
    void missingReasonStillAttributed() {
        // An untagged ledger line is the thing attribution exists to prevent, so a lazy caller
        // must not be able to produce one.
        assertEquals("[mycasino] (no reason given)",
            EconomyHandleImpl.attributeReason("mycasino", null));
        assertEquals("[mycasino] (no reason given)",
            EconomyHandleImpl.attributeReason("mycasino", "   "));
        assertEquals("[mycasino] (no reason given)",
            EconomyHandleImpl.attributeReason("mycasino", ""));
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed")
    void reasonIsTrimmed() {
        assertEquals("[mycasino] roulette payout",
            EconomyHandleImpl.attributeReason("mycasino", "  roulette payout  "));
    }

    @Test
    @DisplayName("an over-long reason is cut to the protocol's limit, keeping the mod tag")
    void longReasonIsTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append('x');
        }
        String attributed = EconomyHandleImpl.attributeReason("mycasino", sb.toString());
        assertEquals(EconomyHandleImpl.MAX_REASON_LENGTH, attributed.length(),
            "truncating here rather than in the request builder keeps the log and the ledger "
                + "reading the same");
        assertTrue(attributed.startsWith("[mycasino] "),
            "the mod tag must survive truncation or the entry stops being attributable");
    }

    @Test
    @DisplayName("a reason exactly at the limit is left alone")
    void reasonAtLimitIsUntouched() {
        String prefix = "[mycasino] ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < EconomyHandleImpl.MAX_REASON_LENGTH - prefix.length(); i++) {
            sb.append('x');
        }
        String attributed = EconomyHandleImpl.attributeReason("mycasino", sb.toString());
        assertEquals(EconomyHandleImpl.MAX_REASON_LENGTH, attributed.length());
        assertEquals(prefix + sb, attributed);
    }
}
