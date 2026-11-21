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

/** Drinking this turns the player into a vapor speedster (permanently, until /vaporous revoke). */
public class LivingMistPhialItem extends Item {

    public LivingMistPhialItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);

        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            boolean already = SpeedsterManager.isPowered(player);
            SpeedsterManager.setPowered(player, true);
            ServerWorld sw = (ServerWorld) world;
            sw.spawnParticles(VaporRegistry.VAPOR, player.getX(), player.getBodyY(0.5), player.getZ(),
                    80, 0.45, 0.9, 0.45, 0.03);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BREEZE_INHALE,
                    SoundCategory.PLAYERS, 1.0f, 1.3f);
            player.sendMessage(Text.translatable(already ? "message.vaporous.already" : "message.vaporous.awakened")
                    .formatted(Formatting.AQUA), false);
        }

        if (user instanceof PlayerEntity player && !player.getAbilities().creativeMode) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (result.isEmpty()) return bottle;
            if (!player.getInventory().insertStack(bottle)) player.dropItem(bottle, false);
        }
        return result;
    }
}
