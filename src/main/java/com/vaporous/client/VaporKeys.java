package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.net.VaporPayloads;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * V – toggle vapor form (the speed power)
 * Z – toggle time dilation
 * R – hold to compress, release to fire a Pressure Jet (sneak + look down to jet upward)
 * G – hold to compress, release to open a Vacuum Well (sneak to open it right in front of you)
 * All rebindable under Options > Controls > Key Binds > Vaporous.
 */
public final class VaporKeys {
    public static final int MAX_CHARGE_TICKS = 30;

    public static KeyBinding form;
    public static KeyBinding time;
    public static KeyBinding blast;
    public static KeyBinding vacuum;

    public static int blastHold;
    public static int vacuumHold;
    private static int blastCooldown;

    private VaporKeys() {}

    public static void register() {
        form = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vaporous.form", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.vaporous"));
        time = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vaporous.time", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.vaporous"));
        blast = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vaporous.blast", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.vaporous"));
        vacuum = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vaporous.vacuum", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.vaporous"));
    }

    static void handle(MinecraftClient mc, ClientPlayerEntity p) {
        if (blastCooldown > 0) blastCooldown--;

        if (!ClientVaporState.powered || p.isSpectator()) {
            while (form.wasPressed()) { /* swallow */ }
            while (time.wasPressed()) { /* swallow */ }
            blastHold = 0;
            vacuumHold = 0;
            return;
        }

        while (form.wasPressed()) send(VaporPayloads.Ability.TOGGLE_FORM, 0f);
        while (time.wasPressed()) send(VaporPayloads.Ability.TOGGLE_TIME, 0f);

        boolean canCharge = mc.currentScreen == null;

        if (canCharge && blast.isPressed()) {
            blastHold = Math.min(blastHold + 1, MAX_CHARGE_TICKS);
        } else if (blastHold > 0) {
            float charge = blastHold / (float) MAX_CHARGE_TICKS;
            blastHold = 0;
            if (blastCooldown == 0) {
                blastCooldown = VaporConfig.get().blastCooldownTicks;
                send(VaporPayloads.Ability.PRESSURE_BLAST, charge);
                if (p.isSneaking()) {
                    // Jet propulsion: the blast pushes you the opposite way. Look down to launch upward.
                    Vec3d dir = p.getRotationVec(1.0f);
                    double thrust = 0.9 + 1.6 * charge;
                    p.addVelocity(-dir.x * thrust, -dir.y * thrust, -dir.z * thrust);
                    p.fallDistance = 0f;
                }
            }
        }

        if (canCharge && vacuum.isPressed()) {
            vacuumHold = Math.min(vacuumHold + 1, MAX_CHARGE_TICKS);
        } else if (vacuumHold > 0) {
            float charge = vacuumHold / (float) MAX_CHARGE_TICKS;
            vacuumHold = 0;
            send(VaporPayloads.Ability.VACUUM, charge);
        }
    }

    private static void send(int ability, float charge) {
        if (ClientPlayNetworking.canSend(VaporPayloads.Ability.ID)) {
            ClientPlayNetworking.send(new VaporPayloads.Ability(ability, charge));
        }
    }
}
