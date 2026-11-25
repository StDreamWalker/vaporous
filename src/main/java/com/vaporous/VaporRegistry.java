package com.vaporous;

import com.vaporous.power.SpeedsterPhialItem;
import com.vaporous.power.Variant;
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
    /** The wispy, translucent vapor used for the trail and the pressure jet. */
    public static final SimpleParticleType VAPOR = FabricParticleTypes.simple(true);
    /** The same dust sprites, white-hot and glowing: the plasma form's trail. */
    public static final SimpleParticleType PLASMA = FabricParticleTypes.simple(true);

    /** Drink it to become a vapor speedster. */
    public static final Item LIVING_MIST_PHIAL = new SpeedsterPhialItem(phialSettings(Rarity.EPIC), Variant.VAPOR);
    /** Drink it to become a plasma speedster. */
    public static final Item LIVING_PLASMA_PHIAL = new SpeedsterPhialItem(phialSettings(Rarity.EPIC), Variant.PLASMA);

    public static final RegistryKey<DamageType> PRESSURE_JET =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Vaporous.id("pressure_jet"));
    public static final RegistryKey<DamageType> KINETIC_IMPACT =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Vaporous.id("kinetic_impact"));

    private VaporRegistry() {}

    private static Item.Settings phialSettings(Rarity rarity) {
        return new Item.Settings()
                .maxCount(16)
                .rarity(rarity)
                .food(new FoodComponent.Builder().nutrition(0).saturationModifier(0f).alwaysEdible().build());
    }

    public static void init() {
        Registry.register(Registries.PARTICLE_TYPE, Vaporous.id("vapor"), VAPOR);
        Registry.register(Registries.PARTICLE_TYPE, Vaporous.id("plasma"), PLASMA);
        Registry.register(Registries.ITEM, Vaporous.id("living_mist_phial"), LIVING_MIST_PHIAL);
        Registry.register(Registries.ITEM, Vaporous.id("living_plasma_phial"), LIVING_PLASMA_PHIAL);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
            entries.add(LIVING_MIST_PHIAL);
            entries.add(LIVING_PLASMA_PHIAL);
        });
    }

    /** The trail/jet particle for a phial variant. */
    public static SimpleParticleType particleFor(int variant) {
        return variant == Variant.PLASMA ? PLASMA : VAPOR;
    }

    public static DamageSource damage(ServerWorld world, RegistryKey<DamageType> key, @Nullable Entity attacker) {
        RegistryEntry<DamageType> type = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key);
        return attacker == null ? new DamageSource(type) : new DamageSource(type, attacker, attacker);
    }
}
