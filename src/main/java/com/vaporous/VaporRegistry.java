package com.vaporous;

import com.vaporous.power.LivingMistPhialItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Rarity;
import org.jetbrains.annotations.Nullable;

public final class VaporRegistry {
    /** The wispy, translucent vapor used for the trail and the pressure attacks. */
    public static final SimpleParticleType VAPOR = FabricParticleTypes.simple(true);

    /** Drink it to become vaporous. */
    public static final Item LIVING_MIST_PHIAL = new LivingMistPhialItem(new Item.Settings()
            .maxCount(16)
            .rarity(Rarity.EPIC)
            .food(new FoodComponent.Builder().nutrition(0).saturationModifier(0f).alwaysEdible().build()));

    public static final RegistryKey<DamageType> PRESSURE_JET =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Vaporous.id("pressure_jet"));
    public static final RegistryKey<DamageType> VACUUM_COLLAPSE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Vaporous.id("vacuum_collapse"));

    private VaporRegistry() {}

    public static void init() {
        Registry.register(Registries.PARTICLE_TYPE, Vaporous.id("vapor"), VAPOR);
        Registry.register(Registries.ITEM, Vaporous.id("living_mist_phial"), LIVING_MIST_PHIAL);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> entries.add(LIVING_MIST_PHIAL));
    }

    public static DamageSource damage(ServerWorld world, RegistryKey<DamageType> key, @Nullable Entity attacker) {
        RegistryEntry<DamageType> type = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key);
        return attacker == null ? new DamageSource(type) : new DamageSource(type, attacker, attacker);
    }
}
