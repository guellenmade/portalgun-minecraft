package com.portal1.entity;

import com.portal1.Portal1Mod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Portal1Mod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PortalEntity>> PORTAL =
        ENTITIES.registerEntityType(
            "portal",
            PortalEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(1.2f, 2.0f).noSummon().fireImmune().clientTrackingRange(12).updateInterval(2)
        );

    public static final DeferredHolder<EntityType<?>, EntityType<PortalProjectileEntity>> PORTAL_PROJECTILE =
        ENTITIES.registerEntityType(
            "portal_projectile",
            PortalProjectileEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.35f, 0.35f).noSummon().fireImmune().clientTrackingRange(10).updateInterval(1)
        );

    public static final DeferredHolder<EntityType<?>, EntityType<WeightedCubeEntity>> WEIGHTED_CUBE =
        ENTITIES.registerEntityType(
            "weighted_cube",
            WeightedCubeEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(WeightedCubeEntity.CUBE_SIZE, WeightedCubeEntity.CUBE_SIZE).fireImmune().clientTrackingRange(8).updateInterval(1)
        );

    private ModEntities() {}
}
