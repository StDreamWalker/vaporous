package com.vaporous;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaporous.power.Variant;
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

    /** Stats that differ between the two phials. */
    public static final class FormStats {
        /** Extra walk speed the moment the form is switched on (1.0 = +100%). */
        public double baseSpeedBonus = 1.0;
        /** Extra walk speed added at 100% vapor (9.0 = +900%). */
        public double maxSpeedBonus = 9.0;
        /** Ground speed (blocks/second) at which the body reaches 100% vapor. */
        public double fullVaporSpeed = 40.0;
        /** Cruising Slipstream flight speed at 100% vapor, in blocks per tick (3.0 = 60 blocks/s). */
        public double flightSpeed = 3.0;
        /** How many times slower the world runs when Time Dilation is switched on. */
        public int baseTimeFactor = 8;
        /** Slowdown reached at 100% vapor when timeScalesWithSpeed is true. */
        public int maxTimeFactor = 20;
        /** Hunger drained per tick while the form is on (scaled by speed). 0 = none. */
        public float speedHungerCost = 0.012f;
        /** Hunger drained per tick while Time Dilation is on. 0 = none. */
        public float timeHungerCost = 0.035f;

        static FormStats plasma() {
            FormStats f = new FormStats();
            f.baseSpeedBonus = 2.0;
            f.maxSpeedBonus = 22.0;      // sprinting at full plasma is roughly 140 blocks/s
            f.fullVaporSpeed = 90.0;
            f.flightSpeed = 6.0;         // 120 blocks/s in flight
            f.baseTimeFactor = 24;
            f.maxTimeFactor = 60;
            f.speedHungerCost = 0f;      // plasma form costs no extra hunger
            f.timeHungerCost = 0f;
            return f;
        }

        void sanitize() {
            if (fullVaporSpeed < 8.0) fullVaporSpeed = 8.0;
            if (baseTimeFactor < 2) baseTimeFactor = 2;
            if (maxTimeFactor < baseTimeFactor) maxTimeFactor = baseTimeFactor;
            if (maxTimeFactor > 100) maxTimeFactor = 100;
            if (flightSpeed < 0.5) flightSpeed = 0.5;
            if (flightSpeed > 9.0) flightSpeed = 9.0; // vanilla rejects moves over ~10 blocks/tick
            speedHungerCost = Math.max(0f, speedHungerCost);
            timeHungerCost = Math.max(0f, timeHungerCost);
        }
    }

    // ---------- Per-phial stats ----------
    /** Phial of Living Mist. */
    public FormStats vapor = new FormStats();
    /** Phial of Living Plasma: much deeper time dilation, much faster, no hunger cost. */
    public FormStats plasma = FormStats.plasma();

    // ---------- Shared movement (applied on the client that owns the player) ----------
    /** How quickly the body thins while accelerating (per tick, fraction of the gap). */
    public double vaporBuildRate = 0.035;
    /** How quickly the body re-condenses when slowing down. */
    public double vaporDecayRate = 0.06;
    /** Extra jump strength at 100% vapor (vanilla base is 0.42). */
    public double jumpBonus = 0.45;
    /** Fraction of gravity removed at 100% vapor (lower density = lighter body). */
    public double gravityReduction = 0.4;
    /** Extra step-up height as soon as the form is on (vanilla is 0.6). */
    public double stepHeightBonus = 0.5;
    /** Further step-up height added at 100% vapor (so up to 0.6 + 0.5 + 1.5 = 2.6 blocks). */
    public double maxStepHeightBonus = 1.5;
    /** Glue to the ground when running off ledges and down slopes instead of launching off them. */
    public boolean stepDown = true;
    /** Deepest drop (blocks) you'll snap down at 100% vapor. Scales up from 0.6 with speed. */
    public double maxStepDown = 2.5;
    /** Vapor needed before you can run up walls. */
    public double wallClimbVapor = 0.25;
    /** Vapor needed before holding jump lets air currents carry you. */
    public double airRideVapor = 0.35;
    /** Stops the speed boost from stretching your field of view to the max. */
    public boolean capFov = true;

    // ---------- Slipstream flight (density 0-20%) ----------
    /** Vapor needed to fly (0.8 = density 20% or lower). */
    public double flightVapor = 0.8;
    /** How quickly your flight path bends toward where you're looking (0-1). Lower = more momentum. */
    public double flightTurnRate = 0.18;
    /** Look at least this many degrees upward while running to take off from the ground. */
    public float takeoffPitch = 12f;

    // ---------- Momentum combat (server) ----------
    /** Extra melee damage at 100% vapor. */
    public double momentumDamage = 8.0;
    /** Extra melee knockback at 100% vapor (used when you're not charging forward). */
    public double momentumKnockback = 2.5;
    /** Extra attack speed at 100% vapor (vanilla base is 4.0). */
    public double attackSpeedBonus = 6.0;
    /** In speed form, your hits ignore the target's invulnerability frames so you can land flurries. */
    public boolean flurryIgnoresIFrames = true;
    /** Extra safe-fall distance (blocks) at 100% vapor. */
    public double safeFallBonus = 14.0;
    /** Newton's cradle: hitting something while moving forward hands it your momentum. */
    public boolean cradleStrikes = true;
    /** Minimum forward speed (blocks/tick; 0.35 = 7 blocks/s) for a cradle strike. */
    public double cradleMinSpeed = 0.35;
    /** Share of your momentum handed to the target (1.0 = all of it, like a Newton's cradle). */
    public double cradleTransfer = 1.0;
    /** Upward lift added to launched targets so they leave the ground. */
    public double cradleLift = 0.25;
    /** Speed cap for anything launched (blocks/tick). */
    public double maxLaunchSpeed = 6.0;
    /** Launched creatures that slam into a block faster than this (blocks/tick) take kinetic damage. */
    public double impactMinSpeed = 0.5;
    /** Kinetic damage per block/tick of impact speed above the minimum. */
    public double impactDamagePerSpeed = 8.0;
    /** A launched creature that hits another creature passes the momentum on, like a cradle. */
    public boolean cradleChains = true;
    /** Share of your forward speed added to anything you shoot or throw. */
    public double projectileMomentum = 1.0;

    // ---------- Catching & throwing (server + client) ----------
    /** How far away (blocks) you can snatch a projectile out of the air while time is dilated. */
    public double catchRange = 6.0;
    /** Ticks of holding right-click for a full-power arrow throw (a trident needs 10). */
    public int throwChargeTicks = 20;
    /** Arrow throw speed with no charge, and extra speed at full charge (a full bow shot is 3.0). */
    public float throwBaseSpeed = 1.2f;
    public float throwChargeSpeed = 2.0f;

    // ---------- Item pickup ----------
    /** Speedsters scoop up items instantly, along the whole path they ran, even in slowed time. */
    public boolean fastPickup = true;
    /** Pickup reach (blocks) around your path. */
    public double pickupRange = 2.0;

    // ---------- Pressure Jet (server) ----------
    /**
     * Outside of slowed time you can only compress air while already moving fast: this much vapor
     * (0.5 = density 50% or lower). Inside slowed time you can use it even standing still.
     */
    public float pressureMinVapor = 0.5f;
    public float blastDamage = 7.0f;
    public float blastVaporBonus = 5.0f;
    public double blastRange = 24.0;
    public int blastCooldownTicks = 16;

    // ---------- Time dilation ----------
    /** The faster you are moving, the slower the world gets. */
    public boolean timeScalesWithSpeed = true;
    /** Pitch multiplier for all sounds while time is dilated (1.0 = off). */
    public float slowSoundPitch = 0.65f;
    /** Faint haze on screen while time is dilated. */
    public boolean slowTint = true;

    // ---------- Speedometer (client) ----------
    public boolean showSpeedometer = true;
    /** "mph", "kmh" or "bps" (blocks per second). 1 block = 1 metre. */
    public String speedUnits = "mph";

    // ---------- Trail visuals (client) ----------
    /** Multiplier for how many wisps are emitted. 0 disables the trail. */
    public float trailDensity = 1.0f;
    /** Starting opacity of each vapor wisp (0-1). Lower = more translucent. */
    public float trailAlpha = 0.30f;
    /** Vapor wisp colour as #RRGGBB. */
    public String trailColor = "#DCE6EE";
    /** Plasma: colour of the white-hot core, the glow it cools to, and the fringe it fades out at. */
    public String plasmaCoreColor = "#FFFFFF";
    public String plasmaGlowColor = "#7FE7FF";
    public String plasmaFringeColor = "#A45CFF";
    /** Starting opacity of each plasma wisp. */
    public float plasmaAlpha = 0.6f;

    public static VaporConfig get() {
        return instance;
    }

    public FormStats stats(int variant) {
        return variant == Variant.PLASMA ? plasma : vapor;
    }

    public static int rgb(String hex, int fallback) {
        try {
            String s = hex.startsWith("#") ? hex.substring(1) : hex;
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    public int trailRgb() {
        return rgb(trailColor, 0xDCE6EE);
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
        if (vapor == null) vapor = new FormStats();
        if (plasma == null) plasma = FormStats.plasma();
        vapor.sanitize();
        plasma.sanitize();
        if (trailColor == null) trailColor = "#DCE6EE";
        if (plasmaCoreColor == null) plasmaCoreColor = "#FFFFFF";
        if (plasmaGlowColor == null) plasmaGlowColor = "#7FE7FF";
        if (plasmaFringeColor == null) plasmaFringeColor = "#A45CFF";
        if (speedUnits == null) speedUnits = "mph";
        trailAlpha = Math.max(0.02f, Math.min(1f, trailAlpha));
        plasmaAlpha = Math.max(0.02f, Math.min(1f, plasmaAlpha));
        flightVapor = Math.max(0.05, Math.min(1.0, flightVapor));
        flightTurnRate = Math.max(0.01, Math.min(1.0, flightTurnRate));
        if (throwChargeTicks < 1) throwChargeTicks = 1;
        if (maxLaunchSpeed > 9.0) maxLaunchSpeed = 9.0;
    }
}
