package com.micatechnologies.minecraft.sum.jail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the jail: cell boxes, sentences, cell choice, duration parsing and the
 * save round trip.
 */
class JailTest {

    private static JailCell cell(String name, int dim, BlockPos a, BlockPos b) {
        return new JailCell(name, dim, a, b, 0.5, 65.0, 0.5, 90.0F);
    }

    private static Inmate inmate(String cell, long jailedAt, long releaseAt) {
        return new Inmate(UUID.randomUUID(), "Prisoner", cell, "Warden", jailedAt, releaseAt);
    }

    // --- JailCell ---

    @Test
    void cellNormalizesCornersAndIsInclusive() {
        JailCell c = cell("A", 0, new BlockPos(4, 70, 4), new BlockPos(0, 64, 0));
        assertEquals(new BlockPos(0, 64, 0), c.getMin());
        assertEquals(new BlockPos(4, 70, 4), c.getMax());
        assertTrue(c.contains(0, new BlockPos(0, 64, 0)));
        assertTrue(c.contains(0, new BlockPos(4, 70, 4)));
        assertFalse(c.contains(0, new BlockPos(5, 65, 2)));
        assertFalse(c.contains(0, new BlockPos(2, 71, 2)));
    }

    @Test
    void cellOnlyContainsItsOwnDimension() {
        JailCell c = cell("A", -1, new BlockPos(0, 64, 0), new BlockPos(4, 70, 4));
        assertTrue(c.contains(-1, new BlockPos(2, 65, 2)));
        assertFalse(c.contains(0, new BlockPos(2, 65, 2)));
    }

    @Test
    void playerPositionUsesTheBlockTheirFeetAreIn() {
        JailCell c = cell("A", 0, new BlockPos(0, 64, 0), new BlockPos(4, 66, 4));
        assertTrue(c.containsPlayerAt(0, 2.5, 65.0, 2.5));
        assertTrue(c.containsPlayerAt(0, 0.01, 64.0, 4.99), "fractional coords floor into the box");
        assertFalse(c.containsPlayerAt(0, -0.01, 65.0, 2.5), "just past the min face is outside");
    }

    @Test
    void cellRoundTripsThroughNbt() {
        JailCell c = new JailCell("Block-C_1", 7, new BlockPos(-3, 10, 9), new BlockPos(3, 14, 12),
            1.25, 11.0, 10.75, -45.0F);
        JailCell back = JailCell.readFromNBT(c.writeToNBT());
        assertNotNull(back);
        assertEquals("Block-C_1", back.getName());
        assertEquals(7, back.getDimensionId());
        assertEquals(c.getMin(), back.getMin());
        assertEquals(c.getMax(), back.getMax());
        assertEquals(1.25, back.getSpawnX());
        assertEquals(11.0, back.getSpawnY());
        assertEquals(10.75, back.getSpawnZ());
        assertEquals(-45.0F, back.getSpawnYaw());
    }

    @Test
    void cellWithoutBoxIsRejectedOnLoad() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", "broken");
        assertNull(JailCell.readFromNBT(tag));
    }

    // --- Inmate ---

    @Test
    void openEndedSentenceNeverEnds() {
        Inmate i = inmate("A", 1000L, Inmate.NO_RELEASE);
        assertFalse(i.hasRelease());
        assertFalse(i.isSentenceOver(Long.MAX_VALUE));
        assertEquals(-1L, i.remainingMillis(5000L));
    }

    @Test
    void timedSentenceEndsAtItsReleaseTime() {
        Inmate i = inmate("A", 1000L, 61_000L);
        assertFalse(i.isSentenceOver(60_999L));
        assertTrue(i.isSentenceOver(61_000L));
        assertEquals(1L, i.remainingMillis(60_999L));
        assertEquals(0L, i.remainingMillis(99_999L), "remaining time never goes negative");
    }

    @Test
    void inmateRoundTripsThroughNbt() {
        Inmate i = inmate("Cell1", 123L, 456L);
        Inmate back = Inmate.readFromNBT(i.writeToNBT());
        assertNotNull(back);
        assertEquals(i.getPlayerId(), back.getPlayerId());
        assertEquals("Prisoner", back.getPlayerName());
        assertEquals("Cell1", back.getCellName());
        assertEquals("Warden", back.getJailedBy());
        assertEquals(123L, back.getJailedAtMillis());
        assertEquals(456L, back.getReleaseAtMillis());
    }

    // --- JailSavedData ---

    @Test
    void cellLookupIgnoresCase() {
        JailSavedData data = new JailSavedData();
        data.putCell(cell("Block-A", 0, BlockPos.ORIGIN, BlockPos.ORIGIN));
        assertNotNull(data.getCell("block-a"));
        assertNotNull(data.getCell("BLOCK-A"));
        assertTrue(data.removeCell("block-A"));
        assertTrue(data.getCells().isEmpty());
    }

    @Test
    void leastOccupiedCellSpreadsInmatesAndBreaksTiesByAge() {
        JailSavedData data = new JailSavedData();
        assertNull(data.leastOccupiedCell(), "no cells, no choice");
        JailCell first = cell("first", 0, BlockPos.ORIGIN, BlockPos.ORIGIN);
        JailCell second = cell("second", 0, BlockPos.ORIGIN, BlockPos.ORIGIN);
        data.putCell(first);
        data.putCell(second);
        assertSame(first, data.leastOccupiedCell(), "tie goes to the earliest cell");
        data.putInmate(inmate("first", 0L, 0L));
        assertSame(second, data.leastOccupiedCell());
        data.putInmate(inmate("SECOND", 0L, 0L));
        data.putInmate(inmate("second", 0L, 0L));
        assertSame(first, data.leastOccupiedCell(), "inmate cell names match case-insensitively");
    }

    @Test
    void savedDataRoundTripsThroughNbt() {
        JailSavedData data = new JailSavedData();
        data.putCell(cell("one", 0, BlockPos.ORIGIN, new BlockPos(2, 2, 2)));
        data.putCell(cell("two", 1, BlockPos.ORIGIN, new BlockPos(3, 3, 3)));
        Inmate held = inmate("two", 10L, 20L);
        data.putInmate(held);

        JailSavedData back = new JailSavedData();
        back.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        assertEquals(2, back.getCells().size());
        assertEquals("one", back.getCells().iterator().next().getName(), "cell order survives");
        assertTrue(back.isJailed(held.getPlayerId()));
        assertEquals(1, back.countInmates("two"));
        assertNotNull(back.findInmateByName("prisoner"));
    }

    @Test
    void refreshNameKeepsTheNewNameFindable() {
        JailSavedData data = new JailSavedData();
        Inmate held = inmate("one", 0L, 0L);
        data.putInmate(held);
        data.refreshName(held.getPlayerId(), "Renamed");
        assertNull(data.findInmateByName("Prisoner"));
        assertNotNull(data.findInmateByName("renamed"));
    }

    // --- Linked doors and alarm relays ---

    @Test
    void linksToggleAndRoundTripThroughNbt() {
        JailSavedData data = new JailSavedData();
        JailCell c = cell("A", 0, BlockPos.ORIGIN, new BlockPos(4, 4, 4));
        data.putCell(c);
        assertTrue(data.toggleDoor(c, new BlockPos(5, 1, 2)), "first toggle links");
        assertTrue(data.toggleAlarm(c, new BlockPos(9, 1, 9)));
        assertTrue(data.toggleDoor(c, new BlockPos(6, 1, 2)));
        assertFalse(data.toggleDoor(c, new BlockPos(6, 1, 2)), "second toggle unlinks");

        JailCell back = JailCell.readFromNBT(c.writeToNBT());
        assertNotNull(back);
        assertEquals(1, back.getDoors().size());
        assertTrue(back.getDoors().contains(new BlockPos(5, 1, 2)));
        assertTrue(back.getAlarms().contains(new BlockPos(9, 1, 9)));
    }

    @Test
    void redrawingACellKeepsItsLinks() {
        JailSavedData data = new JailSavedData();
        JailCell original = cell("A", 0, BlockPos.ORIGIN, new BlockPos(4, 4, 4));
        data.putCell(original);
        data.toggleDoor(original, new BlockPos(5, 1, 2));
        data.toggleAlarm(original, new BlockPos(9, 1, 9));

        JailCell redrawn = cell("a", 0, BlockPos.ORIGIN, new BlockPos(6, 6, 6));
        data.putCell(redrawn);
        assertSame(redrawn, data.getCell("A"));
        assertTrue(redrawn.getDoors().contains(new BlockPos(5, 1, 2)));
        assertTrue(redrawn.getAlarms().contains(new BlockPos(9, 1, 9)));
    }

    @Test
    void aDoorIsFoundByEitherOfItsBlocksInItsOwnDimension() {
        JailSavedData data = new JailSavedData();
        JailCell c = cell("A", 0, BlockPos.ORIGIN, new BlockPos(4, 4, 4));
        data.putCell(c);
        data.toggleDoor(c, new BlockPos(5, 1, 2));
        assertSame(c, data.cellForDoor(0, new BlockPos(5, 1, 2)));
        assertSame(c, data.cellForDoor(0, new BlockPos(5, 2, 2)), "upper half of the door");
        assertNull(data.cellForDoor(0, new BlockPos(5, 3, 2)), "two above is another block");
        assertNull(data.cellForDoor(0, new BlockPos(6, 1, 2)));
        assertNull(data.cellForDoor(-1, new BlockPos(5, 1, 2)), "other dimension");
    }

    // --- Durations ---

    @Test
    void bareNumberIsMinutes() {
        assertEquals(30 * 60_000L, JailService.parseDuration("30"));
    }

    @Test
    void unitSuffixesParse() {
        assertEquals(90_000L, JailService.parseDuration("90s"));
        assertEquals(5 * 60_000L, JailService.parseDuration("5M"));
        assertEquals(2 * 3_600_000L, JailService.parseDuration("2h"));
        assertEquals(86_400_000L, JailService.parseDuration(" 1d "));
    }

    @Test
    void badDurationsAreRejected() {
        assertEquals(-1L, JailService.parseDuration(""));
        assertEquals(-1L, JailService.parseDuration("0"));
        assertEquals(-1L, JailService.parseDuration("-5m"));
        assertEquals(-1L, JailService.parseDuration("5w"));
        assertEquals(-1L, JailService.parseDuration("cellA"));
        assertEquals(-1L, JailService.parseDuration("h"));
        assertEquals(-1L, JailService.parseDuration("99999999999999999d"), "overflow is refused");
    }

    @Test
    void durationsFormatCompactly() {
        assertEquals("0s", JailService.formatDuration(0L));
        assertEquals("1s", JailService.formatDuration(1L), "a partial second rounds up");
        assertEquals("40s", JailService.formatDuration(40_000L));
        assertEquals("1h 5m", JailService.formatDuration(3_900_000L));
        assertEquals("1d 2h 3m 4s", JailService.formatDuration(93_784_000L));
    }

    @Test
    void cellNamesAreRestricted() {
        assertTrue(CommandSetJail.NAME.matcher("Block-C_12").matches());
        assertFalse(CommandSetJail.NAME.matcher("has space").matches());
        assertFalse(CommandSetJail.NAME.matcher("").matches());
        assertFalse(CommandSetJail.NAME.matcher("a23456789012345678901234567890123").matches());
    }
}
