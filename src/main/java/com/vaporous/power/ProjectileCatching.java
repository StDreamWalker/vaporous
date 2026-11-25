package com.vaporous.power;

import com.vaporous.VaporConfig;
import com.vaporous.VaporRegistry;
import com.vaporous.time.TimeDilation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Snatching projectiles out of slowed time, and throwing arrows by hand.
 * <ul>
 *   <li><b>Catch</b> (either phial, time dilated): right-click an arrow, trident, snowball, egg, pearl,
 *   potion, firework, fireball... in flight. Ones that have an item form go into your inventory;
 *   anything else is plucked out of the air and dispersed.</li>
 *   <li><b>Throw</b> (any time): hold right-click with arrows in hand to wind up like a trident,
 *   release to throw. Speed (and so damage) scales with the wind-up and with your forward momentum.</li>
 * </ul>
 */
public final class ProjectileCatching {
    private ProjectileCatching() {}

    public static void tryCatch(ServerPlayerEntity p, int entityId) {
        int variant = SpeedsterManager.variant(p);
        if (variant == Variant.NONE || p.isSpectator() || !p.isAlive()) return;
        if (!TimeDilation.SERVER.active()) return;

        ServerWorld world = p.getServerWorld();
        Entity e = world.getEntityById(entityId);
        if (!(e instanceof ProjectileEntity projectile) || e instanceof FishingBobberEntity || !e.isAlive()) return;
        double range = VaporConfig.get().catchRange + 2.0; // slack for latency
        if (p.squaredDistanceTo(e) > range * range) return;

        ItemStack caught = ItemStack.EMPTY;
        if (projectile instanceof PersistentProjectileEntity arrowLike) {
            caught = arrowLike.getItemStack().copy();
        } else if (projectile instanceof FlyingItemEntity flying) {
            caught = flying.getStack().copy();
        }

        Vec3d at = e.getPos();
        e.discard();

        if (!caught.isEmpty()) {
            if (!p.getInventory().insertStack(caught)) p.dropItem(caught, false);
        }
        world.spawnParticles(VaporRegistry.particleFor(variant), at.x, at.y, at.z, 10, 0.12, 0.12, 0.12, 0.02);
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.7f, 1.6f);
        p.swingHand(Hand.MAIN_HAND, true);
    }

    public static void throwArrow(ServerPlayerEntity p, float charge, boolean offhand) {
        if (!SpeedsterManager.isPowered(p) || p.isSpectator() || !p.isAlive()) return;
        if (Float.isNaN(charge)) return;
        charge = MathHelper.clamp(charge, 0f, 1f);
        if (charge < 0.2f) return;

        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack stack = p.getStackInHand(hand);
        if (!(stack.getItem() instanceof ArrowItem arrowItem)) return;

        VaporConfig c = VaporConfig.get();
        ServerWorld world = p.getServerWorld();
        PersistentProjectileEntity arrow = arrowItem.createArrow(world, stack.copyWithCount(1), p, null);
        float speed = c.throwBaseSpeed + c.throwChargeSpeed * charge;
        arrow.setVelocity(p, p.getPitch(), p.getYaw(), 0.0f, speed, 1.2f - charge);
        arrow.setCritical(charge >= 1.0f);
        if (p.getAbilities().creativeMode) {
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
        } else {
            stack.decrement(1);
        }
        // Forward momentum is added as the arrow enters the world (see MomentumCombat).
        world.spawnEntity(arrow);

        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS, 1.0f, 0.8f + 0.3f * charge);
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.5f, 1.6f);
        p.swingHand(hand, true);
    }
}
