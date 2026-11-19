package com.portal1.entity;

import com.portal1.core.PlayerPortalRegistry;
import com.portal1.core.PortalBasis;
import com.portal1.core.PortalColorScheme;
import com.portal1.core.PortalSurfaceRules;
import com.portal1.world.PortalWorldData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Visible, traveling Portal 1 projectile (NOT instant hitscan like Portal 2).
 *
 * Travels through the world with a glowing energy trail in the shooter's primary or secondary color.
 * On surface contact, validates portalability & placement rules:
 * - Valid portalable surface -> opens/relocates the shooter's portal of that type.
 * - Invalid surface (black, metal, glass, non-full, overlap) -> fizzles with particles & sound.
 */
public final class PortalProjectileEntity extends Entity {

    public static final double SHOT_SPEED = 3.2; // blocks/tick (64 blocks/sec, brisk and clearly visible)
    public static final int MAX_LIFETIME_TICKS = 100;

    private static final EntityDataAccessor<Integer> DATA_COLOR_RGB =
        SynchedEntityData.defineId(PortalProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_SECONDARY =
        SynchedEntityData.defineId(PortalProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerUuid;
    private float shooterYaw;
    private int channel;

    public PortalProjectileEntity(EntityType<? extends PortalProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static PortalProjectileEntity create(
        ServerLevel level,
        UUID ownerUuid,
        Vec3 startPos,
        Vec3 direction,
        float shooterYaw,
        boolean secondary,
        int colorRgb
    ) {
        return create(level, ownerUuid, 0, startPos, direction, shooterYaw, secondary, colorRgb);
    }

    public static PortalProjectileEntity create(
        ServerLevel level,
        UUID ownerUuid,
        int channel,
        Vec3 startPos,
        Vec3 direction,
        float shooterYaw,
        boolean secondary,
        int colorRgb
    ) {
        PortalProjectileEntity proj = new PortalProjectileEntity(ModEntities.PORTAL_PROJECTILE.get(), level);
        proj.ownerUuid = ownerUuid;
        proj.channel = channel;
        proj.shooterYaw = shooterYaw;
        proj.setPos(startPos);
        proj.setDeltaMovement(direction.normalize().scale(SHOT_SPEED));
        proj.entityData.set(DATA_IS_SECONDARY, secondary);
        proj.entityData.set(DATA_COLOR_RGB, colorRgb);
        return proj;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR_RGB, PortalColorScheme.CLASSIC.primaryRgb());
        builder.define(DATA_IS_SECONDARY, false);
    }

    public int getColorRgb() {
        return this.entityData.get(DATA_COLOR_RGB);
    }

    public boolean isSecondary() {
        return this.entityData.get(DATA_IS_SECONDARY);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 currentPos = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 nextPos = currentPos.add(vel);

        if (this.level().isClientSide()) {
            // Visible traveling energy trail on client
            DustParticleOptions dust = new DustParticleOptions(getColorRgb(), 1.15f);
            for (int i = 0; i < 3; i++) {
                double frac = i / 3.0;
                Vec3 p = currentPos.lerp(nextPos, frac);
                this.level().addParticle(dust, p.x, p.y, p.z, 0.0, 0.0, 0.0);
            }
            this.setPos(nextPos);
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();
        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        // Server-side particle stream for all observers
        DustParticleOptions dust = new DustParticleOptions(getColorRgb(), 1.1f);
        serverLevel.sendParticles(dust, currentPos.x, currentPos.y, currentPos.z, 2, 0.04, 0.04, 0.04, 0.0);

        // Continuous swept raycast to guarantee zero tunneling through thin walls
        BlockHitResult hit = serverLevel.clip(new ClipContext(
            currentPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            handleImpact(serverLevel, hit);
            this.discard();
            return;
        }

        this.setPos(nextPos);
    }

    private void handleImpact(ServerLevel level, BlockHitResult hit) {
        Direction face = hit.getDirection();
        PortalBasis basis = PortalBasis.fromFaceAndShooterYaw(face, this.shooterYaw);
        PortalWorldData worldData = PortalWorldData.get(level);

        UUID existingSameSlotId = null;
        if (this.ownerUuid != null) {
            Optional<PlayerPortalRegistry.PortalPair> pairOpt = worldData.getPair(this.ownerUuid, this.channel);
            if (pairOpt.isPresent()) {
                existingSameSlotId = isSecondary() ? pairOpt.get().secondaryId() : pairOpt.get().primaryId();
            }
        }

        // Gather all nearby existing portals (from ALL players) to prevent overlap
        AABB nearbyBox = new AABB(hit.getLocation(), hit.getLocation()).inflate(8.0);
        List<PortalSurfaceRules.ExistingPortalRef> nearbyPortals = new ArrayList<>();
        for (PortalEntity p : level.getEntitiesOfClass(PortalEntity.class, nearbyBox)) {
            if (p.isAlive()) {
                nearbyPortals.add(new PortalSurfaceRules.ExistingPortalRef(
                    p.getUUID(), p.position(), p.getPortalBasis().facing()
                ));
            }
        }

        Optional<Vec3> validPos = PortalSurfaceRules.findValidPlacement(
            level, hit.getLocation(), hit.getBlockPos(), basis, nearbyPortals, existingSameSlotId
        );

        if (validPos.isEmpty() || this.ownerUuid == null) {
            // Fizzle on invalid/non-portalable surface (Portal.fizzle_invalid_surface)
            spawnFizzle(level, hit.getLocation(), getColorRgb());
            return;
        }

        // Open new portal and register lightweight endpoint under owner's (owner, channel) pair
        PortalEntity newPortal = PortalEntity.create(
            level, validPos.get(), basis, this.ownerUuid, isSecondary(), getColorRgb()
        );
        PlayerPortalRegistry.PortalEndpoint newEndpoint = PlayerPortalRegistry.PortalEndpoint.of(
            newPortal.getUUID(), validPos.get(), basis, getColorRgb()
        );
        PlayerPortalRegistry.PlacementResult reg = worldData.registerPortal(
            this.ownerUuid, this.channel, isSecondary(), newEndpoint
        );

        // Close ONLY the owner's previous portal of the same channel & slot (NEVER another channel or player)
        if (reg.replacedPortalId() != null) {
            Entity old = level.getEntity(reg.replacedPortalId());
            if (old instanceof PortalEntity oldPortal && oldPortal.isAlive()) {
                oldPortal.discard();
            }
        }

        level.addFreshEntity(newPortal);

        // Link both portals (works even if partner is in an unloaded chunk!)
        if (reg.partnerEndpoint() != null) {
            Entity partnerEnt = level.getEntity(reg.partnerPortalId());
            if (partnerEnt instanceof PortalEntity partner && partner.isAlive()) {
                newPortal.syncLinkWith(partner);
                partner.syncLinkWith(newPortal);
            } else {
                newPortal.syncLinkWithEndpoint(reg.partnerEndpoint(), -1);
            }
        }

        // Open sound & burst particles
        level.playSound(
            null, hit.getBlockPos(),
            SoundEvents.BEACON_ACTIVATE,
            SoundSource.BLOCKS,
            0.55f,
            isSecondary() ? 1.15f : 1.45f
        );
        DustParticleOptions dust = new DustParticleOptions(getColorRgb(), 1.25f);
        for (int i = 0; i < 16; i++) {
            double a = (Math.PI * 2.0 * i) / 16.0;
            Vec3 p = validPos.get()
                .add(basis.right().scale(Math.cos(a) * PortalBasis.HALF_WIDTH))
                .add(basis.up().scale(Math.sin(a) * PortalBasis.HALF_HEIGHT))
                .add(basis.forward().scale(0.06));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
    }

    private static void spawnFizzle(ServerLevel level, Vec3 pos, int colorRgb) {
        DustParticleOptions dust = new DustParticleOptions(colorRgb, 1.3f);
        level.sendParticles(dust, pos.x, pos.y, pos.z, 18, 0.25, 0.25, 0.25, 0.08);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.55f, 1.6f);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.getString("OwnerUuid").ifPresent(s -> {
            try {
                this.ownerUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {}
        });
        this.shooterYaw = input.getFloatOr("ShooterYaw", 0.0f);
        this.channel = input.getIntOr("Channel", 0);
        this.entityData.set(DATA_IS_SECONDARY, input.getBooleanOr("IsSecondary", false));
        this.entityData.set(DATA_COLOR_RGB, input.getIntOr("ColorRgb", PortalColorScheme.CLASSIC.primaryRgb()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.ownerUuid != null) {
            output.putString("OwnerUuid", this.ownerUuid.toString());
        }
        output.putFloat("ShooterYaw", this.shooterYaw);
        output.putInt("Channel", this.channel);
        output.putBoolean("IsSecondary", isSecondary());
        output.putInt("ColorRgb", getColorRgb());
    }
}
