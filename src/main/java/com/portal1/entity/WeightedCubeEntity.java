package com.portal1.entity;

import com.portal1.world.CarryManager;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Aperture Science Weighted Storage Cube (Portal 1 models/props/metal_box.mdl, 40.5 HU^3 -> 0.85 blocks^3).
 *
 * Physics entity that can be picked up and carried in front of the player via CarryManager
 * (Portal 1 CPlayerPickupController behavior) and teleports through portals with full momentum preservation.
 */
public final class WeightedCubeEntity extends Entity {

    public static final float CUBE_SIZE = 0.85f;
    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.98;
    private static final double GROUND_FRICTION = 0.72;

    private static final EntityDataAccessor<Boolean> DATA_CARRIED =
        SynchedEntityData.defineId(WeightedCubeEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID carrierUuid;

    public WeightedCubeEntity(EntityType<? extends WeightedCubeEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CARRIED, false);
    }

    public UUID getCarrierUuid() {
        return this.carrierUuid;
    }

    public boolean isCarried() {
        return this.entityData.get(DATA_CARRIED);
    }

    public void setCarrier(UUID playerUuid) {
        this.carrierUuid = playerUuid;
        this.entityData.set(DATA_CARRIED, playerUuid != null);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vel = this.getDeltaMovement();

        if (!isCarried()) {
            vel = vel.add(0.0, -GRAVITY, 0.0);
        }

        this.move(MoverType.SELF, vel);

        Vec3 afterVel = this.getDeltaMovement();
        if (!isCarried()) {
            double drag = this.onGround() ? GROUND_FRICTION : AIR_DRAG;
            double nextY = this.onGround() ? 0.0 : afterVel.y * AIR_DRAG;
            this.setDeltaMovement(afterVel.x * drag, nextY, afterVel.z * drag);
        }
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return !isCarried();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return !isCarried();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Weighted cubes are indestructible in Portal 1 (except by fizzlers/incinerators);
        // in Creative, players can still break/remove them cleanly if needed.
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player && player.isCreative()) {
            CarryManager.forceDropCube(this);
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Never persist an active carry tether across restarts
        this.carrierUuid = null;
        this.entityData.set(DATA_CARRIED, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // No extra persistent fields required
    }
}
