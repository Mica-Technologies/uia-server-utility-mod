package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.events.EventManager;
import cc.polyfrost.oneconfig.events.event.ReceivePacketEvent;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe;
import net.minecraft.network.play.server.SPacketTimeUpdate;

/**
 * Server tick-rate estimate. Reads {@link SPacketTimeUpdate}, which vanilla servers
 * send every server-tick second (every 20 server ticks). Real-time gap between two
 * consecutive packets tells us how fast the server is actually running — a healthy
 * server delivers them ~1000ms apart for a TPS reading of 20.00; a lagging server
 * stretches the gap and the reading drops.
 *
 * <p>Approach matches EvergreenHUD's TPS element. OneConfig's {@link EventManager}
 * delivers the receive-packet event; we register on construction since OneConfig owns
 * the HUD module's lifecycle and the HUD can't get a Forge subscription set up
 * otherwise.</p>
 */
public class TpsHud extends SingleTextHud {

    /** Wall-clock millis of the previous SPacketTimeUpdate arrival, or 0 before any. */
    private transient long lastUpdated = 0L;
    private transient String tpsText = "—";

    public TpsHud() {
        super("TPS:", false, 5, 5);
        EventManager.INSTANCE.register(this);
    }

    @Subscribe
    public void onTimeUpdate(ReceivePacketEvent event) {
        if (!(event.packet instanceof SPacketTimeUpdate)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastUpdated > 0L) {
            long delta = now - lastUpdated;
            if (delta > 0L) {
                // 20 server ticks per packet × 1000ms/s ÷ real-millis-elapsed = TPS.
                // Clamp to [0, 20] so a hiccup that produces an outlier doesn't display
                // 50 TPS.
                double tps = Math.min(20.0, Math.max(0.0, 20000.0 / delta));
                tpsText = String.format("%.2f", tps);
            }
        }
        lastUpdated = now;
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "20.00";
        }
        return tpsText;
    }
}
