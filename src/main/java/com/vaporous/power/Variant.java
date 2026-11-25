package com.vaporous.power;

/** Which phial a player drank. Stored on the player as a number. */
public final class Variant {
    public static final int NONE = 0;
    /** Phial of Living Mist: translucent vapor. */
    public static final int VAPOR = 1;
    /** Phial of Living Plasma: emissive plasma, far deeper time dilation, far faster, no hunger cost. */
    public static final int PLASMA = 2;

    private Variant() {}

    public static boolean isValid(int v) {
        return v == VAPOR || v == PLASMA;
    }

    public static String name(int v) {
        return v == PLASMA ? "plasma" : v == VAPOR ? "vapor" : "none";
    }
}
