package com.portal1.item;

import com.portal1.entity.ModEntities;
import com.portal1.entity.WeightedCubeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns an Aperture Science Weighted Storage Cube entity in the world.
 */
public final class WeightedCubeItem extends Item {

    public WeightedCubeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos().relative(face);
        Vec3 spawnPos = Vec3.atBottomCenterOf(pos);

        WeightedCubeEntity cube = new WeightedCubeEntity(ModEntities.WEIGHTED_CUBE.get(), serverLevel);
        cube.setPos(spawnPos);
        if (context.getPlayer() != null) {
            cube.setYRot(context.getPlayer().getYRot());
        }
        serverLevel.addFreshEntity(cube);
        serverLevel.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.7f, 1.0f);

        if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
