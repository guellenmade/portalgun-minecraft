package com.portal1.world;

import com.portal1.Portal1Mod;
import com.portal1.item.PortalGunItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge 26.2 custom C2S and S2C networking for Portal 1 firing, carrying, and smooth client sync.
 */
public final class PortalNetworking {

    public record FirePortalPayload(boolean secondary) implements CustomPacketPayload {
        public static final Type<FirePortalPayload> TYPE = new Type<>(Portal1Mod.id("fire_portal"));
        public static final StreamCodec<FriendlyByteBuf, FirePortalPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FirePortalPayload::secondary,
            FirePortalPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ToggleCarryPayload() implements CustomPacketPayload {
        public static final Type<ToggleCarryPayload> TYPE = new Type<>(Portal1Mod.id("toggle_carry"));
        public static final StreamCodec<FriendlyByteBuf, ToggleCarryPayload> CODEC = StreamCodec.unit(new ToggleCarryPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PortalVelocitySyncPayload(double vx, double vy, double vz, float yaw, float pitch) implements CustomPacketPayload {
        public static final Type<PortalVelocitySyncPayload> TYPE = new Type<>(Portal1Mod.id("portal_velocity_sync"));
        public static final StreamCodec<FriendlyByteBuf, PortalVelocitySyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, PortalVelocitySyncPayload::vx,
            ByteBufCodecs.DOUBLE, PortalVelocitySyncPayload::vy,
            ByteBufCodecs.DOUBLE, PortalVelocitySyncPayload::vz,
            ByteBufCodecs.FLOAT, PortalVelocitySyncPayload::yaw,
            ByteBufCodecs.FLOAT, PortalVelocitySyncPayload::pitch,
            PortalVelocitySyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private PortalNetworking() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(FirePortalPayload.TYPE, FirePortalPayload.CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (player.getMainHandItem().getItem() instanceof PortalGunItem
                        || player.getOffhandItem().getItem() instanceof PortalGunItem) {
                        PortalGunItem.firePortal(player, payload.secondary());
                    }
                }
            })
        );

        registrar.playToServer(ToggleCarryPayload.TYPE, ToggleCarryPayload.CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    CarryManager.toggleCarry(player);
                }
            })
        );

        registrar.playToClient(PortalVelocitySyncPayload.TYPE, PortalVelocitySyncPayload.CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                var player = context.player();
                if (player != null) {
                    var exitVel = new net.minecraft.world.phys.Vec3(payload.vx(), payload.vy(), payload.vz());
                    player.setDeltaMovement(exitVel);
                    com.portal1.client.Portal1ModClient.setPendingExitVelocity(exitVel);
                    player.setYRot(payload.yaw());
                    player.setXRot(payload.pitch());
                    player.yRotO = payload.yaw();
                    player.xRotO = payload.pitch();
                    player.yHeadRot = payload.yaw();
                    player.yHeadRotO = payload.yaw();
                }
            })
        );
    }

    public static void sendVelocitySync(ServerPlayer player, double vx, double vy, double vz, float yaw, float pitch) {
        PacketDistributor.sendToPlayer(player, new PortalVelocitySyncPayload(vx, vy, vz, yaw, pitch));
    }
}
