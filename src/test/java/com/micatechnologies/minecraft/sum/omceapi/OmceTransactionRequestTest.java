package com.micatechnologies.minecraft.sum.omceapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Wire-shape and invariant checks for the request SUM sends most often. */
class OmceTransactionRequestTest {

    private static final UUID BUYER = UUID.fromString("f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2");
    private static final UUID SELLER = UUID.fromString("3c2a1b09-77aa-4c11-9d0e-51ab2c9f0011");

    private static OmceParty buyer() {
        return OmceParty.player(BUYER, "SomePlayer", "acct_1");
    }

    @Test
    @DisplayName("a shop purchase serialises to the documented shape")
    void shopPurchaseShape() {
        OmceTransactionRequest request = OmceTransactionRequest.builder(
                OmceProtocol.TX_SHOP_PURCHASE, 12_500L, buyer(),
                OmceParty.shop("shop:overworld:120:64:-33", "Alex's Emporium", SELLER))
            .initiator(BUYER, "SomePlayer", OmceTransactionRequest.ROLE_PLAYER)
            .reason("Purchased 16x Diamond")
            .meta("item", "minecraft:diamond")
            .build();

        JsonObject json = request.toJson("SUM", "alto-main", "2026-07-31T18:22:03.980Z");

        assertEquals("shop_purchase", json.get("type").getAsString());
        assertEquals(12_500L, json.get("amount").getAsLong());
        assertEquals("SUM", json.get("currency").getAsString());
        assertEquals("alto-main", json.get("instance").getAsString());
        assertEquals(request.getIdempotencyKey(), json.get("idempotencyKey").getAsString());

        JsonObject source = json.getAsJsonObject("source");
        assertEquals("player", source.get("type").getAsString());
        assertEquals(BUYER.toString(), source.get("playerUuid").getAsString(),
            "UUIDs must be canonical hyphenated lowercase");
        assertEquals("acct_1", source.get("accountId").getAsString());

        JsonObject destination = json.getAsJsonObject("destination");
        assertEquals("shop", destination.get("type").getAsString());
        assertEquals("shop:overworld:120:64:-33", destination.get("id").getAsString());

        assertEquals("player", json.getAsJsonObject("initiator").get("role").getAsString());
        assertEquals("minecraft:diamond",
            json.getAsJsonObject("metadata").get("item").getAsString());
        assertFalse(json.has("fee"), "no fee means no fee object");
        assertFalse(json.has("allowNegative"), "the default must not be sent");
    }

    @Test
    @DisplayName("a fee is carried alongside the amount, not subtracted from it")
    void feeShape() {
        OmceTransactionRequest request = OmceTransactionRequest.builder(
                OmceProtocol.TX_PLAYER_TRANSFER, 5_000L, buyer(),
                OmceParty.player(SELLER, "Bob", null))
            .fee(100L, OmceParty.system("sink.pay_fee"))
            .build();

        JsonObject json = request.toJson("SUM", "alto-main", null);
        assertEquals(5_000L, json.get("amount").getAsLong(), "the sender is charged the full amount");

        JsonObject fee = json.getAsJsonObject("fee");
        assertEquals(100L, fee.get("amount").getAsLong());
        assertEquals("sink.pay_fee", fee.getAsJsonObject("destination").get("id").getAsString());
    }

    @Test
    @DisplayName("a fee larger than the amount is refused")
    void feeBounds() {
        OmceTransactionRequest.Builder b = OmceTransactionRequest.builder(
            OmceProtocol.TX_PLAYER_TRANSFER, 100L, buyer(), OmceParty.player(SELLER, "Bob", null));
        assertThrows(IllegalArgumentException.class, () -> b.fee(101L, null));
        assertThrows(IllegalArgumentException.class, () -> b.fee(-1L, null));
    }

    @Test
    @DisplayName("a transaction with no player party is refused before it reaches the network")
    void requiresSettledParty() {
        assertThrows(IllegalArgumentException.class, () -> OmceTransactionRequest.builder(
            OmceProtocol.TX_SHOP_PAYOUT, 100L,
            OmceParty.shop("shop:a", null, null),
            OmceParty.system("sink.x")));
    }

    @Test
    @DisplayName("a negative amount is refused — direction comes from the parties")
    void refusesNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> OmceTransactionRequest.builder(
            OmceProtocol.TX_SHOP_PURCHASE, -1L, buyer(), OmceParty.cash("atm:1")));
    }

    @Test
    @DisplayName("attaching resolved account ids keeps the idempotency key stable")
    void resolvingAccountsPreservesKey() {
        OmceTransactionRequest request = OmceTransactionRequest.builder(
            OmceProtocol.TX_ATM_WITHDRAW, 10_000L,
            OmceParty.player(BUYER, "SomePlayer", null),
            OmceParty.cash("atm:overworld:88:70:12")).build();

        OmceTransactionRequest resolved = request.withResolvedAccounts("acct_99", null);

        // If this ever changed, a retry after a timeout would look like a brand new operation to
        // the service and could charge the player twice.
        assertEquals(request.getIdempotencyKey(), resolved.getIdempotencyKey());
        assertEquals("acct_99",
            resolved.toJson("SUM", "i", null).getAsJsonObject("source").get("accountId").getAsString());
    }

    @Test
    @DisplayName("metadata is capped rather than allowed to blow the spec limit")
    void metadataLimits() {
        OmceTransactionRequest.Builder b = OmceTransactionRequest.builder(
            OmceProtocol.TX_SHOP_PURCHASE, 1L, buyer(), OmceParty.shop("shop:a", null, null));
        for (int i = 0; i < 40; i++) {
            b.meta("key" + i, "value");
        }
        JsonObject metadata = b.build().toJson("SUM", "i", null).getAsJsonObject("metadata");
        assertEquals(16, metadata.entrySet().size());
    }

    @Test
    @DisplayName("an over-long reason is truncated to the spec limit")
    void reasonTruncated() {
        StringBuilder long512 = new StringBuilder();
        for (int i = 0; i < 512; i++) {
            long512.append('x');
        }
        OmceTransactionRequest request = OmceTransactionRequest.builder(
                OmceProtocol.TX_SHOP_PURCHASE, 1L, buyer(), OmceParty.shop("shop:a", null, null))
            .reason(long512.toString())
            .build();
        assertEquals(256,
            request.toJson("SUM", "i", null).get("reason").getAsString().length());
    }

    @Test
    @DisplayName("an explicit idempotency key survives, so a persisted operation can be replayed")
    void explicitIdempotencyKey() {
        OmceTransactionRequest request = OmceTransactionRequest.builder(
                OmceProtocol.TX_LOYALTY_REWARD, 100L,
                OmceParty.system("faucet.loyalty"), buyer())
            .idempotencyKey("fixed-key-1234")
            .build();
        assertEquals("fixed-key-1234", request.getIdempotencyKey());
    }

    @Test
    @DisplayName("positionId builds a stable world-and-position identifier")
    void positionIdShape() {
        assertEquals("shop:overworld:120:64:-33",
            OmceParty.positionId("shop", "overworld", 120, 64, -33));
        assertEquals("atm:world:0:0:0", OmceParty.positionId("atm", "", 0, 0, 0));
    }

    @Test
    @DisplayName("admin initiation is visible to the service for authorization")
    void adminRoleFlagged() {
        OmceTransactionRequest request = OmceTransactionRequest.builder(
                OmceProtocol.TX_ADMIN_CREDIT, 50_000L, OmceParty.system("faucet.admin"), buyer())
            .initiator(SELLER, "AnAdmin", OmceTransactionRequest.ROLE_ADMIN)
            .build();
        assertTrue(request.isAdminInitiated());
        assertEquals("admin",
            request.toJson("SUM", "i", null).getAsJsonObject("initiator").get("role").getAsString());
    }
}
