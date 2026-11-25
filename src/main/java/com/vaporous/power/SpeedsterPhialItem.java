package com.vaporous.power;

import com.vaporous.VaporRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Drinking a phial turns the player into a speedster of that variant (permanently, until
 * /vaporous revoke). Drinking the other phial switches variant.
 */
public class SpeedsterPhialItem extends Item {
    private final int variant;

    public SpeedsterPhialItem(Settings settings, int variant) {
        super(settings);
        this.variant = variant;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return variant == Variant.PLASMA || super.hasGlint(stack);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);

        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            int before = SpeedsterManager.variant(player);
            SpeedsterManager.setVariant(player, variant);
            ServerWorld sw = (ServerWorld) world;
            sw.spawnParticles(VaporRegistry.particleFor(variant), player.getX(), player.getBodyY(0.5), player.getZ(),
                    80, 0.45, 0.9, 0.45, 0.03);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    variant == Variant.PLASMA ? SoundEvents.BLOCK_BEACON_POWER_SELECT : SoundEvents.ENTITY_BREEZE_INHALE,
                    SoundCategory.PLAYERS, 1.0f, variant == Variant.PLASMA ? 0.7f : 1.3f);

            String key;
            if (before == variant) key = "message.vaporous.already";
            else if (variant == Variant.PLASMA) key = before == Variant.NONE ? "message.vaporous.awakened_plasma" : "message.vaporous.ignited";
            else key = before == Variant.NONE ? "message.vaporous.awakened" : "message.vaporous.cooled";
            player.sendMessage(Text.translatable(key)
                    .formatted(variant == Variant.PLASMA ? Formatting.LIGHT_PURPLE : Formatting.AQUA), false);
        }

        if (user instanceof PlayerEntity player && !player.getAbilities().creativeMode) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (result.isEmpty()) return bottle;
            if (!player.getInventory().insertStack(bottle)) player.dropItem(bottle, false);
        }
        return result;
    }
}
