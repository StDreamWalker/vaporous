package com.vaporous.power;

/**
 * Kept so that uploading the new src folder over an older copy replaces this file cleanly.
 * The phials now use {@link SpeedsterPhialItem}.
 */
@Deprecated
public class LivingMistPhialItem extends SpeedsterPhialItem {
    public LivingMistPhialItem(Settings settings) {
        super(settings, Variant.VAPOR);
    }
}
