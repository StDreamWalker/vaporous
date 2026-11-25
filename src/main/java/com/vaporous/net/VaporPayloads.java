package com.vaporous.net;

import com.vaporous.Vaporous;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public final class VaporPayloads {
    private VaporPayloads() {}

    // ----------------------------------------------------------------- client -> server

    /** A key press: toggle the speed form, toggle time dilation, or fire the Pressure Jet. */
    public record Ability(int ability, float charge) implements CustomPayload {
        public static final int TOGGLE_FORM = 0;
        public static final int TOGGLE_TIME = 1;
        public static final int PRESSURE_BLAST = 2;

        public static final Id<Ability> ID = new Id<>(Vaporous.id("ability"));
        public static final PacketCodec<RegistryByteBuf, Ability> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Ability::ability,
                PacketCodecs.FLOAT, Ability::charge,
                Ability::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * The owning client measures its own speed and reports how vaporous it currently is, and whether
     * the power is holding it up this tick (flying, wall-running, riding currents) so the server
     * doesn't count that as falling.
     */
    public record VaporReport(float vapor, boolean floating) implements CustomPayload {
        public static final Id<VaporReport> ID = new Id<>(Vaporous.id("vapor_report"));
        public static final PacketCodec<RegistryByteBuf, VaporReport> CODEC = PacketCodec.tuple(
                PacketCodecs.FLOAT, VaporReport::vapor,
                PacketCodecs.BOOL, VaporReport::floating,
                VaporReport::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Right-clicked a projectile in slowed time: snatch it out of the air. */
    public record Catch(int entityId) implements CustomPayload {
        public static final Id<Catch> ID = new Id<>(Vaporous.id("catch"));
        public static final PacketCodec<RegistryByteBuf, Catch> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Catch::entityId,
                Catch::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Released a charged arrow throw (charge 0..1) from the main hand or off hand. */
    public record Throw(float charge, boolean offhand) implements CustomPayload {
        public static final Id<Throw> ID = new Id<>(Vaporous.id("throw"));
        public static final PacketCodec<RegistryByteBuf, Throw> CODEC = PacketCodec.tuple(
                PacketCodecs.FLOAT, Throw::charge,
                PacketCodecs.BOOL, Throw::offhand,
                Throw::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ----------------------------------------------------------------- server -> client

    /** State of one speedster (variant 0 = not a speedster), sent to that player and everyone who can see them. */
    public record VaporState(int entityId, int variant, boolean form, boolean time, float vapor) implements CustomPayload {
        public static final Id<VaporState> ID = new Id<>(Vaporous.id("vapor_state"));
        public static final PacketCodec<RegistryByteBuf, VaporState> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, VaporState::entityId,
                PacketCodecs.VAR_INT, VaporState::variant,
                PacketCodecs.BOOL, VaporState::form,
                PacketCodecs.BOOL, VaporState::time,
                PacketCodecs.FLOAT, VaporState::vapor,
                VaporState::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Global world slowdown factor (1 = normal speed). */
    public record TimeFactor(int factor) implements CustomPayload {
        public static final Id<TimeFactor> ID = new Id<>(Vaporous.id("time_factor"));
        public static final PacketCodec<RegistryByteBuf, TimeFactor> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, TimeFactor::factor,
                TimeFactor::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
