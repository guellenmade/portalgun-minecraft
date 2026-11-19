package com.portal1.item;

import com.portal1.core.PortalColorScheme;
import com.portal1.entity.PortalProjectileEntity;
import com.portal1.world.CarryManager;
import com.portal1.world.PortalWorldData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Aperture Science Handheld Portal Device (4 color-coded pair channels: 0..3).
 *
 * - Channel 0: Classic Blue / Orange
 * - Channel 1: Emerald / Magenta
 * - Channel 2: Gold / Violet
 * - Channel 3: Cyan / Crimson
 *
 * Each gun creates its own independent portal pair without affecting the other 3 guns or other players.
 */
public final class PortalGunItem extends Item {

    public static final long FIRE_COOLDOWN_TICKS = 8L;
    private static final Map<UUID, Long> LAST_FIRE_TICK = new HashMap<>();

    private final int channel;

    public PortalGunItem(Properties properties) {
        this(properties, 0);
    }

    public PortalGunItem(Properties properties, int channel) {
        super(properties);
        this.channel = channel;
    }

    public int getChannel() {
        return this.channel;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            firePortal(serverPlayer, this.channel, true);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean firePortal(ServerPlayer player, boolean secondary) {
        int ch = 0;
        if (player.getMainHandItem().getItem() instanceof PortalGunItem gun) {
            ch = gun.getChannel();
        } else if (player.getOffhandItem().getItem() instanceof PortalGunItem gun) {
            ch = gun.getChannel();
        }
        return firePortal(player, ch, secondary);
    }

    public static boolean firePortal(ServerPlayer player, int channel, boolean secondary) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST_FIRE_TICK.get(player.getUUID());
        if (last != null && (now - last) < FIRE_COOLDOWN_TICKS) {
            return false;
        }
        LAST_FIRE_TICK.put(player.getUUID(), now);

        if (CarryManager.isCarrying(player.getUUID())) {
            CarryManager.dropCarried(player);
        }

        PortalColorScheme scheme = PortalWorldData.get(level).getOrAssignColorScheme(player.getUUID(), channel);
        int colorRgb = scheme.colorFor(secondary);

        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 muzzlePos = eyePos.add(look.scale(0.45));

        PortalProjectileEntity projectile = PortalProjectileEntity.create(
            level,
            player.getUUID(),
            channel,
            muzzlePos,
            look,
            player.getYRot(),
            secondary,
            colorRgb
        );
        level.addFreshEntity(projectile);

        level.playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.SHULKER_SHOOT,
            SoundSource.PLAYERS,
            0.75f,
            secondary ? 0.85f : 1.35f
        );
        return true;
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.LivingEntity miner) {
        return false;
    }
}
