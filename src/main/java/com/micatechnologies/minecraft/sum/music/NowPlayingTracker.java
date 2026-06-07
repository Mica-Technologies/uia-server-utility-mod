package com.micatechnologies.minecraft.sum.music;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.Sound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Tracks the background-music track and jukebox record currently playing on the client,
 * for the Now Playing HUD. Registered on the Forge event bus in {@code SumClientProxy}.
 *
 * <p>Capture: Forge fires {@link PlaySoundEvent} at the head of
 * {@code SoundManager.playSound}, before the weighted-random {@code sounds.json} entry is
 * resolved — so we store the {@link ISound} and read {@link ISound#getSound()} lazily;
 * by the time the HUD asks, {@code createAccessor} has resolved the concrete track.</p>
 *
 * <p>Cleanup: there is no "sound stopped" event, so a once-per-second poll of
 * {@code SoundHandler.isSoundPlaying} clears finished/stopped tracks. The same poll also
 * drops sounds that never audibly started (e.g. music volume at 0%, which early-returns
 * inside {@code playSound} after the event has already fired).</p>
 *
 * <p>Titles are derived from the sound file path, which is why the Alto resource pack
 * stores one file per song title ({@code music/alto/game/uptown_vibes.ogg} →
 * "Uptown Vibes").</p>
 */
@SideOnly(Side.CLIENT)
public class NowPlayingTracker {

    /** Poll cadence for the is-it-still-playing check, in client ticks. */
    private static final int POLL_INTERVAL_TICKS = 20;

    /** Path of {@code SoundHandler.MISSING_SOUND} — never a real track. */
    private static final String MISSING_SOUND_PATH = "missing_sound";

    private static ISound currentMusic;
    private static ISound currentRecord;

    private int pollCounter;

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        ISound sound = event.getResultSound();
        if (sound == null) {
            return;
        }
        if (sound.getCategory() == SoundCategory.MUSIC) {
            currentMusic = sound;
        } else if (sound.getCategory() == SoundCategory.RECORDS) {
            currentRecord = sound;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++pollCounter < POLL_INTERVAL_TICKS) {
            return;
        }
        pollCounter = 0;
        if (currentMusic != null
            && !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(currentMusic)) {
            currentMusic = null;
        }
        if (currentRecord != null
            && !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(currentRecord)) {
            currentRecord = null;
        }
    }

    /**
     * Human-readable title of what is currently playing, or {@code null} when silent.
     * A spinning jukebox record wins over background music when both are audible and
     * {@code includeRecords} is set, since the record is what the player actually hears
     * up close.
     */
    public static String getNowPlayingTitle(boolean includeRecords) {
        if (includeRecords) {
            String record = describe(currentRecord);
            if (record != null) {
                return record;
            }
        }
        return describe(currentMusic);
    }

    /** Resolves an {@link ISound} to a display title, or {@code null} if unresolvable. */
    private static String describe(ISound track) {
        if (track == null) {
            return null;
        }
        Sound sound = track.getSound();
        ResourceLocation location = sound != null ? sound.getSoundLocation() : null;
        if (location == null || location.getPath().contains(MISSING_SOUND_PATH)) {
            return null;
        }
        return prettifyTrackPath(location.getPath());
    }

    /**
     * {@code "music/alto/game/uptown_vibes"} → {@code "Uptown Vibes"};
     * {@code "music/game/calm1"} → {@code "Calm 1"} (for packs that keep vanilla names).
     */
    static String prettifyTrackPath(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        // Separate a trailing take number ("calm1" -> "calm 1") and word separators.
        name = name.replaceAll("(?<=[A-Za-z])(\\d+)$", " $1").replace('_', ' ').replace('-', ' ');
        StringBuilder title = new StringBuilder(name.length());
        for (String word : name.trim().split("\\s+")) {
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                title.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return title.toString();
    }
}
