package com.micatechnologies.minecraft.sum.omceapi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micatechnologies.minecraft.sum.omceapi.OmceAccount;
import com.micatechnologies.minecraft.sum.omceapi.OmceBalance;
import com.micatechnologies.minecraft.sum.omceapi.OmceError;
import com.micatechnologies.minecraft.sum.omceapi.OmceEventPage;
import com.micatechnologies.minecraft.sum.omceapi.OmceHealth;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransaction;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Response parsing runs on a background thread against a service we do not control, so it has to
 * survive missing, null, and wrong-typed fields without throwing.
 */
class OmceJsonTest {

    private static JsonObject parse(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    @Test
    @DisplayName("a documented health response is read completely")
    void readsHealth() {
        OmceHealth health = OmceJson.readHealth(parse("{"
            + "\"ok\":true,\"status\":\"ok\","
            + "\"implementation\":{\"name\":\"example-economy\",\"version\":\"3.2.1\"},"
            + "\"currency\":{\"code\":\"SUM\",\"symbol\":\"$\",\"minorUnitDigits\":2},"
            + "\"capabilities\":{\"void\":true,\"holds\":false,\"events\":true,\"fees\":true},"
            + "\"settledPartyTypes\":[\"player\"],"
            + "\"requiredHeaders\":[\"Authorization\",\"X-MCE-Online-Mode\"],"
            + "\"limits\":{\"maxBatchAccounts\":100,\"maxTransactionAmount\":100000000,"
            + "\"requestsPerMinute\":600,\"idempotencyRetentionHours\":72}}"));

        assertNotNull(health);
        assertTrue(health.isWritable());
        assertEquals("SUM", health.getCurrency().getCode());
        assertEquals(2, health.getCurrency().getMinorUnitDigits());
        assertTrue(health.getCapabilities().canVoid());
        assertTrue(health.getCapabilities().hasEvents());
        assertFalse(health.getCapabilities().hasHolds(), "a false flag is not a capability");
        assertFalse(health.getCapabilities().hasLedger(), "an absent flag means false");
        assertEquals(100, health.getLimits().getMaxBatchAccounts());
        assertEquals(2, health.getRequiredHeaders().size());
        assertTrue(health.getImplementationName().contains("example-economy"));
    }

    @Test
    @DisplayName("a sparse health response falls back to safe defaults")
    void healthDefaults() {
        OmceHealth health = OmceJson.readHealth(parse("{\"ok\":true}"));
        assertNotNull(health);
        assertEquals("SUM", health.getCurrency().getCode());
        assertEquals(2, health.getCurrency().getMinorUnitDigits());
        assertTrue(health.getSettledPartyTypes().contains("player"), "player is always settled");
        assertEquals(50, health.getLimits().getMaxBatchAccounts(), "the spec floor is 50");
        assertTrue(health.getRequiredHeaders().isEmpty());
    }

    @Test
    @DisplayName("read_only status is recognised as not writable")
    void readOnlyStatus() {
        OmceHealth health = OmceJson.readHealth(parse("{\"status\":\"read_only\"}"));
        assertTrue(health.isReadOnly());
        assertFalse(health.isWritable());
    }

    @Test
    @DisplayName("a balance is read with available defaulting to the balance")
    void readsBalance() {
        OmceBalance balance = OmceJson.readBalance(parse("{"
            + "\"accountId\":\"acct_10457\","
            + "\"playerUuid\":\"f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2\","
            + "\"balance\":887500,\"version\":4271,\"status\":\"active\"}"));

        assertNotNull(balance);
        assertEquals(887_500L, balance.getBalance());
        assertEquals(887_500L, balance.getAvailable(), "no holds means available == balance");
        assertEquals(0L, balance.getHeld());
        assertEquals(4271L, balance.getVersion());
        assertTrue(balance.isActive());
    }

    @Test
    @DisplayName("a balance object without a balance field is rejected, not defaulted to zero")
    void balanceRequiresAmount() {
        // Defaulting a missing balance to 0 would silently tell a player they are broke.
        assertNull(OmceJson.readBalance(parse("{\"accountId\":\"acct_1\",\"version\":3}")));
    }

    @Test
    @DisplayName("a frozen account is not active")
    void frozenAccount() {
        OmceBalance balance = OmceJson.readBalance(
            parse("{\"accountId\":\"a\",\"balance\":10,\"status\":\"frozen\"}"));
        assertFalse(balance.isActive());
    }

    @Test
    @DisplayName("an error is read with its details intact")
    void readsError() {
        OmceError error = OmceJson.readError(parse("{\"ok\":false,\"error\":{"
            + "\"code\":\"INSUFFICIENT_FUNDS\",\"message\":\"Balance 4200 is below 12500.\","
            + "\"retryable\":false,"
            + "\"details\":{\"balance\":4200,\"required\":12500,\"shortfall\":8300}}}"), 409);

        assertEquals(OmceProtocol.ERR_INSUFFICIENT_FUNDS, error.getCode());
        assertFalse(error.isRetryable());
        assertEquals(8_300L, error.detailLong("shortfall", -1L));
        assertTrue(error.playerMessage(2, "$").contains("$83.00"),
            "the player is told exactly how short they are");
    }

    @Test
    @DisplayName("a missing retryable flag falls back to the code's default")
    void retryableFallsBackToCode() {
        // Assuming false would turn a transient outage into a hard failure.
        OmceError rateLimited = OmceJson.readError(
            parse("{\"error\":{\"code\":\"RATE_LIMITED\"}}"), 429);
        assertTrue(rateLimited.isRetryable());

        OmceError insufficient = OmceJson.readError(
            parse("{\"error\":{\"code\":\"INSUFFICIENT_FUNDS\"}}"), 409);
        assertFalse(insufficient.isRetryable());
    }

    @Test
    @DisplayName("an error-less failure body still yields a usable error from the HTTP status")
    void errorFromStatusAlone() {
        OmceError error = OmceJson.readError(parse("{\"ok\":false}"), 503);
        assertEquals(OmceProtocol.ERR_SERVICE_UNAVAILABLE, error.getCode());
        assertTrue(error.isRetryable());
    }

    @Test
    @DisplayName("player-facing text never echoes the raw diagnostic message")
    void playerMessageIsSanitised() {
        OmceError error = OmceJson.readError(parse("{\"error\":{\"code\":\"UNAUTHENTICATED\","
            + "\"message\":\"token sea_live_abc123 rejected by upstream node 7\"}}"), 401);
        String shown = error.playerMessage(2, "$");
        assertFalse(shown.contains("sea_live_abc123"), shown);
        assertFalse(shown.contains("node 7"), shown);
        assertTrue(shown.contains("administrator"), shown);
    }

    @Test
    @DisplayName("link instructions are relayed to the player verbatim")
    void linkInstructionsRelayed() {
        OmceError error = OmceJson.readError(parse("{\"error\":{\"code\":\"ACCOUNT_NOT_LINKED\","
            + "\"details\":{\"linkInstructions\":\"Run /link in the lobby first.\"}}}"), 409);
        assertEquals("Run /link in the lobby first.", error.playerMessage(2, "$"));
    }

    @Test
    @DisplayName("accounts parse, including unresolved entries")
    void readsAccounts() {
        List<OmceAccount> accounts = OmceJson.readAccounts(parse("{\"accounts\":["
            + "{\"playerUuid\":\"f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2\",\"resolved\":true,"
            + "\"accountId\":\"acct_10457\",\"status\":\"active\"},"
            + "{\"playerUuid\":\"0f2b0c33-6f0f-4d1c-9a80-2d3f1b7d9e11\",\"resolved\":false,"
            + "\"reason\":\"ACCOUNT_NOT_LINKED\"}]}"));

        assertEquals(2, accounts.size());
        assertTrue(accounts.get(0).isUsable());
        assertFalse(accounts.get(1).isUsable(), "an unresolved entry is not an error, but is unusable");
        assertEquals("ACCOUNT_NOT_LINKED", accounts.get(1).getReason());
    }

    @Test
    @DisplayName("an entry with an unparseable UUID is skipped rather than failing the batch")
    void skipsBadUuid() {
        List<OmceAccount> accounts = OmceJson.readAccounts(parse("{\"accounts\":["
            + "{\"playerUuid\":\"not-a-uuid\",\"resolved\":true},"
            + "{\"playerUuid\":\"f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2\",\"resolved\":true,"
            + "\"accountId\":\"acct_1\"}]}"));
        assertEquals(1, accounts.size());
    }

    @Test
    @DisplayName("a transaction is read with its status and replay flag")
    void readsTransaction() {
        OmceTransaction tx = OmceJson.readTransaction(parse("{"
            + "\"transactionId\":\"txn_01J8Z9\",\"status\":\"committed\","
            + "\"type\":\"shop_purchase\",\"amount\":12500,\"fee\":{\"amount\":250},"
            + "\"idempotencyKey\":\"9b1f0a0c\",\"replayed\":true}"));

        assertNotNull(tx);
        assertTrue(tx.isCommitted());
        assertFalse(tx.isPending());
        assertEquals(12_500L, tx.getAmount());
        assertEquals(250L, tx.getFeeAmount());
        assertTrue(tx.isReplayed());
    }

    @Test
    @DisplayName("a pending transaction is not treated as committed")
    void pendingIsNotCommitted() {
        OmceTransaction tx = OmceJson.readTransaction(
            parse("{\"transactionId\":\"t\",\"status\":\"pending\",\"amount\":1}"));
        assertFalse(tx.isCommitted(), "an irreversible effect must never fire on this");
        assertTrue(tx.isPending());
    }

    @Test
    @DisplayName("an event page carries balances and the resume cursor")
    void readsEvents() {
        OmceEventPage page = OmceJson.readEvents(parse("{\"events\":["
            + "{\"cursor\":\"evt_129\",\"kind\":\"balance_changed\","
            + "\"accountId\":\"acct_10457\","
            + "\"playerUuid\":\"f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2\","
            + "\"balance\":900000,\"version\":4274,"
            + "\"transaction\":{\"reason\":\"Weekly payout\"}}],"
            + "\"nextCursor\":\"evt_129\",\"hasMore\":false}"));

        assertNotNull(page);
        assertEquals(1, page.getEvents().size());
        OmceEventPage.Event event = page.getEvents().get(0);
        assertTrue(event.isBalanceChange());
        assertEquals(900_000L, event.getBalance().getBalance());
        assertEquals(4274L, event.getBalance().getVersion());
        assertEquals("Weekly payout", event.getReason());
        assertEquals("evt_129", page.getNextCursor());
        assertFalse(page.hasMore());
    }

    @Test
    @DisplayName("an empty feed is a successful poll, not a failure")
    void emptyEventPage() {
        OmceEventPage page = OmceJson.readEvents(parse("{\"events\":[],\"hasMore\":false}"));
        assertNotNull(page);
        assertTrue(page.isEmpty());
    }

    @Test
    @DisplayName("wrong-typed and null fields fall back instead of throwing")
    void toleratesGarbage() {
        JsonObject o = parse("{\"a\":null,\"b\":{},\"c\":[],\"d\":\"text\"}");
        assertEquals("fallback", OmceJson.string(o, "a", "fallback"));
        assertEquals("fallback", OmceJson.string(o, "b", "fallback"));
        assertEquals(7L, OmceJson.number(o, "d", 7L));
        assertEquals(7L, OmceJson.number(o, "missing", 7L));
        assertTrue(OmceJson.bool(o, "missing", true));
        assertNull(OmceJson.object(o, "c"));
        assertNull(OmceJson.array(o, "b"));
        assertNull(OmceJson.uuid(o, "d"));
        assertTrue(OmceJson.stringList(o, "c").isEmpty());
        assertNull(OmceJson.readHealth(null));
        assertNull(OmceJson.readBalance(null));
        assertNull(OmceJson.readTransaction(null));
    }
}
