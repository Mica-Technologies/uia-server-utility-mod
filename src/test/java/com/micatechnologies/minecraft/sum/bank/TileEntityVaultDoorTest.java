package com.micatechnologies.minecraft.sum.bank;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure SHA-256 passcode logic of {@link TileEntityVaultDoor}. The TE is instantiated
 * with a null world, so {@code markDirty()} is a no-op and the owner/tick/auto-close paths (which
 * need a live world) stay untouched — only the hash-and-compare logic is exercised.
 */
class TileEntityVaultDoorTest {

    @Test
    void correctPasscodeUnlocks() {
        TileEntityVaultDoor door = new TileEntityVaultDoor();
        door.setPasscode("1234");
        assertTrue(door.checkPasscode("1234"));
        assertFalse(door.checkPasscode("0000"));
    }

    @Test
    void passcodeIsCharacterSensitive() {
        TileEntityVaultDoor door = new TileEntityVaultDoor();
        door.setPasscode("Secret");
        assertFalse(door.checkPasscode("secret"), "hash is case-sensitive");
        assertTrue(door.checkPasscode("Secret"));
    }

    @Test
    void unsetPasscodeNeverMatches() {
        TileEntityVaultDoor door = new TileEntityVaultDoor();
        assertFalse(door.hasPasscode());
        assertFalse(door.checkPasscode(""), "an empty hash matches nothing, not even empty input");
        assertFalse(door.checkPasscode("anything"));
    }

    @Test
    void hasPasscodeReflectsSetAndClear() {
        TileEntityVaultDoor door = new TileEntityVaultDoor();
        door.setPasscode("abc");
        assertTrue(door.hasPasscode());
        door.setPasscode("");
        assertFalse(door.hasPasscode(), "empty string clears the passcode");
        door.setPasscode("abc");
        door.setPasscode(null);
        assertFalse(door.hasPasscode(), "null clears the passcode");
    }
}
