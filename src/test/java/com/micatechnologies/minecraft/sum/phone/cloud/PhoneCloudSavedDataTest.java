package com.micatechnologies.minecraft.sum.phone.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure number formatting and allocation logic in {@link PhoneCloudSavedData}:
 * {@code formatNumber} (both the AAA-EEE-XXXX and legacy 7-digit overloads),
 * {@code allocateNumberWithFixedExchange} (floorMod exchange clamp + uniqueness + index
 * registration), and the reservation NBT round-trip. Player-facing accessors that need an
 * {@code EntityPlayerMP} are out of scope; {@code markDirty()} is a harmless flag off-runtime.
 */
class PhoneCloudSavedDataTest {

    private static String middleGroup(String number) {
        return number.split("-")[1];
    }

    // --- formatNumber(area, exchange, subscriber) ---

    @Test
    void formatsFullNumberWithZeroPadding() {
        assertEquals("456-242-0007", PhoneCloudSavedData.formatNumber(456, 242, 7));
        assertEquals("001-002-0003", PhoneCloudSavedData.formatNumber(1, 2, 3));
        assertEquals("987-999-9999", PhoneCloudSavedData.formatNumber(987, 999, 9999));
        assertEquals("456-000-0000", PhoneCloudSavedData.formatNumber(456, 0, 0));
    }

    // --- formatNumber(int) legacy 7-digit ---

    @Test
    void formatsLegacySevenDigitNumber() {
        assertEquals("123-4567", PhoneCloudSavedData.formatNumber(1234567));
        assertEquals("000-0000", PhoneCloudSavedData.formatNumber(0));
        assertEquals("999-9999", PhoneCloudSavedData.formatNumber(9999999));
        assertEquals("000-4567", PhoneCloudSavedData.formatNumber(4567));
    }

    // --- allocateNumberWithFixedExchange ---

    @Test
    void fixedExchangeAppearsInMiddleGroup() {
        PhoneCloudSavedData data = new PhoneCloudSavedData();
        assertEquals("242", middleGroup(data.allocateNumberWithFixedExchange(242, UUID.randomUUID())));
    }

    @Test
    void fixedExchangeIsFloorModded() {
        PhoneCloudSavedData data = new PhoneCloudSavedData();
        assertEquals("242", middleGroup(data.allocateNumberWithFixedExchange(1242, UUID.randomUUID())));
        assertEquals("999", middleGroup(data.allocateNumberWithFixedExchange(-1, UUID.randomUUID())));
    }

    @Test
    void repeatedFixedExchangeAllocationsAreUnique() {
        PhoneCloudSavedData data = new PhoneCloudSavedData();
        String a = data.allocateNumberWithFixedExchange(242, UUID.randomUUID());
        String b = data.allocateNumberWithFixedExchange(242, UUID.randomUUID());
        assertNotEquals(a, b, "allocator must not hand out a number already in the index");
    }

    @Test
    void allocatedNumberResolvesToAssignee() {
        PhoneCloudSavedData data = new PhoneCloudSavedData();
        UUID who = UUID.randomUUID();
        String number = data.allocateNumberWithFixedExchange(242, who);
        assertEquals(who, data.lookupByNumber(number));
    }

    // --- releaseNumber / lookupByNumber ---

    @Test
    void releaseRemovesFromIndex() {
        PhoneCloudSavedData data = new PhoneCloudSavedData();
        String number = data.allocateNumberWithFixedExchange(500, UUID.randomUUID());
        assertEquals(1, countNonNull(data, number));
        data.releaseNumber(number);
        assertNull(data.lookupByNumber(number));
    }

    @Test
    void releaseIsNullAndUnknownSafe() {
        PhoneCloudSavedData data = new PhoneCloudSavedData();
        data.releaseNumber(null);
        data.releaseNumber("");
        data.releaseNumber("456-000-0000"); // never allocated
        assertNull(data.lookupByNumber("456-000-0000"));
    }

    @Test
    void reservationSurvivesNbtRoundTrip() {
        PhoneCloudSavedData data = new PhoneCloudSavedData();
        UUID owner = UUID.randomUUID();
        String number = data.allocateNumberWithFixedExchange(242, owner);

        NBTTagCompound nbt = data.writeToNBT(new NBTTagCompound());
        PhoneCloudSavedData restored = new PhoneCloudSavedData();
        restored.readFromNBT(nbt);
        assertEquals(owner, restored.lookupByNumber(number));
    }

    private static int countNonNull(PhoneCloudSavedData data, String number) {
        return data.lookupByNumber(number) != null ? 1 : 0;
    }
}
