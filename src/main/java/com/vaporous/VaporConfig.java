package com.vaporous;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tunable values, stored in config/vaporous.json. Edit the file and run /vaporous reload
 * (server-side values) or restart the game (client-side movement/visual values).
 */
public final class VaporConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VaporConfig instance = new VaporConfig();

    // ---------- Vapor form: movement (applied on the client that owns the player) ----------
    /** Extra walk speed the moment vapor form is switched on (1.0 = +100%). */
    public double baseSpeedBonus = 1.0;
    /** Extra walk speed added at 100% vapor (9.0 = +900%). Sprinting at full vapor is roughly 60 blocks/s. */
    public double maxSpeedBonus = 9.0;
    /** Ground speed (blocks/second) at which the body reaches 100% vapor. */
    public double fullVaporSpeed = 40.0;
    /** How quickly the body thins while accelerating (per tick, fraction of the gap). */
    public double vaporBuildRate = 0.035;
    /** How quickly the body re-condenses when slowing down. */
    public double vaporDecayRate = 0.06;
    /** Extra jump strength at 100% vapor (vanilla base is 0.42). */
    public double jumpBonus = 0.45;
    /** Fraction of gravity removed at 100% vapor (lower density = lighter body). */
    public double gravityReduction = 0.4;
    /** Extra step height in vapor form so you flow over 1-block ledges at speed. */
    public double stepHeightBonus = 0.5;
    /** Vapor needed before you can run up walls. */
    public double wallClimbVapor = 0.25;
    /** Vapor needed before holding jump lets air currents carry you. */
    public double airRideVapor = 0.35;
    /** Stops the speed boost from stretching your field of view to the max. */
    public boolean capFov = true;

    // ---------- Slipstream flight (density 0-20%) ----------
    /** Vapor needed to fly (0.8 = density 20% or lower). */
    public double flightVapor = 0.8;
    /** Cruising flight speed at 100% vapor, in blocks per tick (3.0 = 60 blocks/s). */
    public double flightSpeed = 3.0;
    /** How quickly your flight path bends toward where you're looking (0-1). Lower = more momentum. */
    public double flightTurnRate = 0.18;
    /** Look at least this many degrees upward while running to take off from the ground. */
    public float takeoffPitch = 12f;

    // ---------- Momentum combat (server) ----------
    /** Extra melee damage at 100% vapor. */
    public double momentumDamage = 8.0;
    /** Extra melee knockback at 100% vapor. */
    public double momentumKnockback = 2.5;
    /** Extra attack speed at 100% vapor (vanilla base is 4.0). */
    public double attackSpeedBonus = 6.0;
    /** In vapor form, your hits ignore the target's invulnerability frames so you can land flurries. */
    public boolean flurryIgnoresIFrames = true;
    /** Extra safe-fall distance (blocks) at 100% vapor. */
    public double safeFallBonus = 14.0;

    // ---------- Pressure attacks (server) ----------
    /**
     * Outside of slowed time you can only compress air while already moving fast: this much vapor
     * (0.5 = density 50% or lower). Inside slowed time you can use them even standing still.
     */
    public float pressureMinVapor = 0.5f;
    public float blastDamage = 7.0f;
    public float blastVaporBonus = 5.0f;
    public double blastRange = 24.0;
    public int blastCooldownTicks = 16;
    public double vacuumRange = 20.0;
    public double vacuumRadius = 7.0;
    public float vacuumDamage = 9.0f;
    public int vacuumCooldownTicks = 60;

    // ---------- Time dilation ----------
    /** How many times slower the world runs when the ability is switched on. */
    public int baseTimeFactor = 8;
    /** Slowdown reached at 100% vapor when timeScalesWithSpeed is true. */
    public int maxTimeFactor = 20;
    /** The faster you are moving, the slower the world gets. */
    public boolean timeScalesWithSpeed = true;
    /** Pitch multiplier for all sounds while time is dilated (1.0 = off). */
    public float slowSoundPitch = 0.65f;
    /** Faint cold haze on screen while time is dilated. */
    public boolean slowTint = true;

    // ---------- Energy cost ----------
    public float speedHungerCost = 0.012f;
    public float timeHungerCost = 0.035f;

    // ---------- Speedometer (client) ----------
    public boolean showSpeedometer = true;
    /** "mph", "kmh" or "bps" (blocks per second). 1 block = 1 metre. */
    public String speedUnits = "mph";

    // ---------- Vapor trail visuals (client) ----------
    /** Multiplier for how many vapor wisps are emitted. 0 disables the trail. */
    public float trailDensity = 1.0f;
    /** Starting opacity of each wisp (0-1). Lower = more translucent. */
    public float trailAlpha = 0.30f;
    /** Wisp colour as #RRGGBB. */
    public String trailColor = "#DCE6EE";

    public static VaporConfig get() {
        return instance;
    }

    public int trailRgb() {
        try {
            String s = trailColor.startsWith("#") ? trailColor.substring(1) : trailColor;
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return 0xDCE6EE;
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("vaporous.json");
    }

    public static void load() {
        Path p = path();
        VaporConfig loaded = null;
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                loaded = GSON.fromJson(r, VaporConfig.class);
            } catch (Exception e) {
                Vaporous.LOGGER.error("Could not read {} — using defaults", p, e);
            }
        }
        instance = loaded != null ? loaded : new VaporConfig();
        instance.sanitize();
        save(); // writes any newly-added fields back to disk
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            Vaporous.LOGGER.error("Could not write vaporous.json", e);
        }
    }

    private void sanitize() {
        if (fullVaporSpeed < 8.0) fullVaporSpeed = 8.0;
        if (baseTimeFactor < 2) baseTimeFactor = 2;
        if (maxTimeFactor < baseTimeFactor) maxTimeFactor = baseTimeFactor;
        if (maxTimeFactor > 60) maxTimeFactor = 60;
        if (trailColor == null) trailColor = "#DCE6EE";
        if (speedUnits == null) speedUnits = "mph";
        flightVapor = Math.max(0.05, Math.min(1.0, flightVapor));
        flightTurnRate = Math.max(0.01, Math.min(1.0, flightTurnRate));
        trailAlpha = Math.max(0.02f, Math.min(1f, trailAlpha));
    }
}
