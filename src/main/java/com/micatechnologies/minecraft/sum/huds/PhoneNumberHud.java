package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.phone.GuiSumPhone;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData;

/**
 * Player's assigned phone number, read from the most recently received
 * {@link PhoneCloudData} snapshot. {@link com.micatechnologies.minecraft.sum.huds.HudStateTracker}
 * fires a one-shot fetch request on world join so this HUD has data without the
 * user needing to open the phone GUI first.
 *
 * <p>Falls back to "—" until the first cloud snapshot arrives — typical latency
 * is one server round-trip after spawn.</p>
 */
public class PhoneNumberHud extends SingleTextHud {

    public PhoneNumberHud() {
        super("Phone", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "555-0123";
        PhoneCloudData cloud = GuiSumPhone.getLastCloud();
        if (cloud == null || cloud.phoneNumber == null || cloud.phoneNumber.isEmpty()) {
            return "—";
        }
        return cloud.phoneNumber;
    }
}
