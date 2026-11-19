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
 * Aperture Science Handheld Portal Device (Portal 1).
 *
 * - Left-Click: Fires Primary Portal projectile (blue in singleplayer / player's primary color).
 * - Right-Click: Fires Secondary Portal projectile (orange in singleplayer / player's secondary color).
 * - Visible traveling projectile that opens a portal upon hitting a portalable surface.
 * - Cannot fire while carrying a Weighted Storage Cube (matches Portal 1 behavior: firing drops or is blocked;
 *   in Portal 1 firing drops the carried object if you try, or wait: in Portal 1 left/right click while holding
 *   a cube drops it or fires — actually in Portal 1 you can still drop with E or fire to drop? Let's allow
 *   normal firing, or drop if carrying; wait: in Portal 1 pressing attack while holding an object drops it,
 *   while we keep carry strictly on the Use key so firing works cleanly!).
 */
public final class PortalGunItem extends Item {

    public static final long FIRE_COOLDOWN_TICKS = 8L;
    private static final Map<UUID, Long> LAST_FIRE_TICK = new HashMap<>();

    public PortalGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            firePortal(serverPlayer, true);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean firePortal(ServerPlayer player, boolean secondary) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST_FIRE_TICK.get(player.getUUID());
        if (last != null && (now - last) < FIRE_COOLDOWN_TICKS) {
            return false;
        }
        LAST_FIRE_TICK.put(player.getUUID(), now);

        // If player is carrying a cube, gently drop it when firing (matches Portal 1)
        if (CarryManager.isCarrying(player.getUUID())) {
            CarryManager.dropCarried(player);
        }

        PortalColorScheme scheme = PortalWorldData.get(level).getOrAssignColorScheme(player.getUUID());
        int colorRgb = scheme.colorFor(secondary);

        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 muzzlePos = eyePos.add(look.scale(0.45));

        PortalProjectileEntity projectile = PortalProjectileEntity.create(
            level,
            player.getUUID(),
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
