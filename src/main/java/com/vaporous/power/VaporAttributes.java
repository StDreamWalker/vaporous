package com.vaporous.power;

import com.vaporous.VaporConfig;
import com.vaporous.Vaporous;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * "As his velocity increases his density and mass decrease."
 * <p>
 * Vapor level (0..1) drives vanilla attributes: faster running, higher jumps, weaker gravity,
 * softer landings, and heavier momentum strikes. Movement attributes are applied by the client
 * that controls the player (movement is client-driven in Minecraft); combat and fall-damage
 * attributes are applied by the server, which is where those are calculated.
 */
public final class VaporAttributes {
    private static final Identifier SPEED = Vaporous.id("vapor_speed");
    private static final Identifier JUMP = Vaporous.id("vapor_jump");
    private static final Identifier GRAVITY = Vaporous.id("vapor_density");
    private static final Identifier STEP = Vaporous.id("vapor_step");
    private static final Identifier SAFE_FALL = Vaporous.id("vapor_safe_fall");
    private static final Identifier FALL_DAMAGE = Vaporous.id("vapor_fall_damage");
    private static final Identifier DAMAGE = Vaporous.id("vapor_momentum_damage");
    private static final Identifier KNOCKBACK = Vaporous.id("vapor_momentum_knockback");
    private static final Identifier ATTACK_SPEED = Vaporous.id("vapor_attack_speed");

    private VaporAttributes() {}

    /** Client side: run on the locally controlled player every tick. */
    public static void applyClient(LivingEntity p, boolean form, float vapor) {
        VaporConfig c = VaporConfig.get();
        set(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED,
                form ? c.baseSpeedBonus + vapor * c.maxSpeedBonus : 0.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(p, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP,
                form ? vapor * c.jumpBonus : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
        set(p, EntityAttributes.GENERIC_GRAVITY, GRAVITY,
                form ? -vapor * c.gravityReduction : 0.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(p, EntityAttributes.GENERIC_STEP_HEIGHT, STEP,
                form ? c.stepHeightBonus : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    /** Server side: run on every powered player every tick. */
    public static void applyServer(LivingEntity p, boolean form, float vapor) {
        VaporConfig c = VaporConfig.get();
        // Quantise so the attribute only changes (and re-syncs) in 5% steps.
        double q = Math.round(vapor * 20.0) / 20.0;
        set(p, EntityAttributes.GENERIC_STEP_HEIGHT, STEP,
                form ? c.stepHeightBonus : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
        set(p, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, SAFE_FALL,
                form ? c.safeFallBonus * (0.25 + 0.75 * q) : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
        set(p, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, FALL_DAMAGE,
                form ? -0.5 * q : 0.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE,
                form ? q * c.momentumDamage : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
        set(p, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, KNOCKBACK,
                form ? q * c.momentumKnockback : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
        set(p, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED,
                form ? (0.15 + 0.85 * q) * c.attackSpeedBonus : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    public static void clearServer(LivingEntity p) {
        applyServer(p, false, 0f);
    }

    public static void clearClient(LivingEntity p) {
        applyClient(p, false, 0f);
    }

    private static void set(LivingEntity e, RegistryEntry<EntityAttribute> attribute, Identifier id,
                            double value, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = e.getAttributeInstance(attribute);
        if (inst == null) return;
        EntityAttributeModifier existing = inst.getModifier(id);
        if (Math.abs(value) < 1.0E-6) {
            if (existing != null) inst.removeModifier(id);
            return;
        }
        if (existing != null && existing.operation() == op && Math.abs(existing.value() - value) < 1.0E-4) return;
        if (existing != null) inst.removeModifier(id);
        inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
    }
}
