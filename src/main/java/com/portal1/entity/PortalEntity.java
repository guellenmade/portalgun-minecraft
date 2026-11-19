package com.portal1.entity;

import com.portal1.core.PlayerPortalRegistry;
import com.portal1.core.PortalBasis;
import com.portal1.core.PortalColorScheme;
import com.portal1.core.PortalSurfaceRules;
import com.portal1.core.PortalTransform;
import com.portal1.world.CarryManager;
import com.portal1.world.PortalNetworking;
import com.portal1.world.PortalWorldData;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Oval Portal 1 entity.
 *
 * Implements:
 * - Exact Portal 1 momentum & orientation transformation on passage.
 * - Floor portal auto-funneling (sv_player_funnel_into_portals).
 * - Floor, wall, and ceiling portal edge cases (including ceiling head-clearance).
 * - Backing surface validation (fizzles if the block behind the portal is broken/moved).
 * - Multiplayer ownership & cross-player traversal (any entity enters -> exits owner's linked partner).
 */
public final class PortalEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_FACING =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_UP_X =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_UP_Y =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_UP_Z =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR_RGB =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_SECONDARY =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LINKED =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_LINKED_X =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LINKED_Y =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LINKED_Z =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LINKED_FACING =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_LINKED_UP_X =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LINKED_UP_Y =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LINKED_UP_Z =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LINKED_ENTITY_ID =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);

    private static final Map<UUID, Long> TELEPORT_COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Vec3> RECENT_VELOCITY = new HashMap<>();
    private static final Map<UUID, Long> RECENT_VELOCITY_TICK = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_SAMPLE_POS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_ENTITY_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_ENTITY_POS_TICK = new HashMap<>();
    private static final long COOLDOWN_TICKS = 6L;
    public static final int OPEN_ANIMATION_TICKS = 6;

    private UUID ownerUuid;
    private UUID linkedPortalUuid;

    public PortalEntity(EntityType<? extends PortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static PortalEntity create(
        ServerLevel level,
        Vec3 center,
        PortalBasis basis,
        UUID ownerUuid,
        boolean secondary,
        int colorRgb
    ) {
        PortalEntity portal = new PortalEntity(ModEntities.PORTAL.get(), level);
        portal.setPos(center);
        portal.ownerUuid = ownerUuid;
        portal.setPortalBasis(basis);
        portal.entityData.set(DATA_IS_SECONDARY, secondary);
        portal.entityData.set(DATA_COLOR_RGB, colorRgb);
        return portal;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FACING, Direction.NORTH.get3DDataValue());
        builder.define(DATA_UP_X, 0.0f);
        builder.define(DATA_UP_Y, 1.0f);
        builder.define(DATA_UP_Z, 0.0f);
        builder.define(DATA_COLOR_RGB, PortalColorScheme.CLASSIC.primaryRgb());
        builder.define(DATA_IS_SECONDARY, false);
        builder.define(DATA_LINKED, false);
        builder.define(DATA_LINKED_X, 0.0f);
        builder.define(DATA_LINKED_Y, 0.0f);
        builder.define(DATA_LINKED_Z, 0.0f);
        builder.define(DATA_LINKED_FACING, Direction.NORTH.get3DDataValue());
        builder.define(DATA_LINKED_UP_X, 0.0f);
        builder.define(DATA_LINKED_UP_Y, 1.0f);
        builder.define(DATA_LINKED_UP_Z, 0.0f);
        builder.define(DATA_LINKED_ENTITY_ID, -1);
    }

    public void setPortalBasis(PortalBasis basis) {
        this.entityData.set(DATA_FACING, basis.facing().get3DDataValue());
        this.entityData.set(DATA_UP_X, (float) basis.up().x);
        this.entityData.set(DATA_UP_Y, (float) basis.up().y);
        this.entityData.set(DATA_UP_Z, (float) basis.up().z);
    }

    public PortalBasis getPortalBasis() {
        Direction dir = Direction.from3DDataValue(this.entityData.get(DATA_FACING));
        Vec3 forward = Vec3.atLowerCornerOf(dir.getUnitVec3i());
        Vec3 up = new Vec3(
            this.entityData.get(DATA_UP_X),
            this.entityData.get(DATA_UP_Y),
            this.entityData.get(DATA_UP_Z)
        );
        return PortalBasis.fromForwardAndUp(dir, forward, up);
    }

    public PortalBasis getLinkedPortalBasis() {
        Direction dir = Direction.from3DDataValue(this.entityData.get(DATA_LINKED_FACING));
        Vec3 forward = Vec3.atLowerCornerOf(dir.getUnitVec3i());
        Vec3 up = new Vec3(
            this.entityData.get(DATA_LINKED_UP_X),
            this.entityData.get(DATA_LINKED_UP_Y),
            this.entityData.get(DATA_LINKED_UP_Z)
        );
        return PortalBasis.fromForwardAndUp(dir, forward, up);
    }

    public Vec3 getLinkedPos() {
        return new Vec3(
            this.entityData.get(DATA_LINKED_X),
            this.entityData.get(DATA_LINKED_Y),
            this.entityData.get(DATA_LINKED_Z)
        );
    }

    public int getLinkedEntityId() {
        return this.entityData.get(DATA_LINKED_ENTITY_ID);
    }

    public int getColorRgb() {
        return this.entityData.get(DATA_COLOR_RGB);
    }

    public boolean isSecondary() {
        return this.entityData.get(DATA_IS_SECONDARY);
    }

    public boolean isLinked() {
        return this.entityData.get(DATA_LINKED);
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public UUID getLinkedPortalUuid() {
        return this.linkedPortalUuid;
    }

    public float getOpenScale(float partialTick) {
        float t = Math.min(1.0f, (this.tickCount + partialTick) / (float) OPEN_ANIMATION_TICKS);
        // Smooth ease-out curve matching Portal 1's snappy portal opening
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    public void syncLinkWithEndpoint(PlayerPortalRegistry.PortalEndpoint partnerEndpoint, int partnerEntityId) {
        if (partnerEndpoint == null) {
            this.linkedPortalUuid = null;
            this.entityData.set(DATA_LINKED, false);
            this.entityData.set(DATA_LINKED_ENTITY_ID, -1);
            return;
        }
        PortalBasis pb = partnerEndpoint.basis();
        this.linkedPortalUuid = partnerEndpoint.portalId();
        this.entityData.set(DATA_LINKED, true);
        this.entityData.set(DATA_LINKED_X, (float) partnerEndpoint.x());
        this.entityData.set(DATA_LINKED_Y, (float) partnerEndpoint.y());
        this.entityData.set(DATA_LINKED_Z, (float) partnerEndpoint.z());
        this.entityData.set(DATA_LINKED_FACING, pb.facing().get3DDataValue());
        this.entityData.set(DATA_LINKED_UP_X, (float) pb.up().x);
        this.entityData.set(DATA_LINKED_UP_Y, (float) pb.up().y);
        this.entityData.set(DATA_LINKED_UP_Z, (float) pb.up().z);
        this.entityData.set(DATA_LINKED_ENTITY_ID, partnerEntityId);
    }

    public void syncLinkWith(PortalEntity partner) {
        if (partner == null || !partner.isAlive()) {
            syncLinkWithEndpoint(null, -1);
            return;
        }
        syncLinkWithEndpoint(
            PlayerPortalRegistry.PortalEndpoint.of(partner.getUUID(), partner.position(), partner.getPortalBasis(), partner.getColorRgb()),
            partner.getId()
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        PortalWorldData worldData = PortalWorldData.get(serverLevel);

        // 0. If this portal was replaced while its chunk was unloaded, cleanly remove the stale portal
        if (this.tickCount % 10 == 0 && worldData.getPair(this.ownerUuid, 0).isPresent()
            && worldData.getPartnerEndpoint(this.getUUID()).isEmpty()
            && !isRegisteredInWorldData(worldData)) {
            this.discard();
            return;
        }

        // 1. Validate backing surface every 5 ticks (matches Portal 1 "Surface removed from behind portal.")
        if (this.tickCount % 5 == 0 && !isBackingSurfaceIntact(serverLevel)) {
            fizzleAndClose(serverLevel);
            return;
        }

        // 2. Resolve partner endpoint from persistent PortalWorldData — works even when the partner's
        //    chunk is unloaded, WITHOUT force-loading chunks or ticking distant machines!
        Optional<PlayerPortalRegistry.PortalEndpoint> partnerOpt = worldData.getPartnerEndpoint(this.getUUID());
        if (partnerOpt.isEmpty()) {
            if (this.isLinked()) {
                syncLinkWithEndpoint(null, -1);
            }
            return;
        }

        PlayerPortalRegistry.PortalEndpoint partnerEndpoint = partnerOpt.get();
        Entity loadedPartner = serverLevel.getEntity(partnerEndpoint.portalId());
        int partnerEntityId = (loadedPartner instanceof PortalEntity p && p.isAlive()) ? p.getId() : -1;
        syncLinkWithEndpoint(partnerEndpoint, partnerEntityId);

        // 3. Process funneling and teleportation for all nearby entities (including other players!)
        PortalBasis entryBasis = getPortalBasis();
        AABB scanBox = new AABB(this.position(), this.position()).inflate(
            entryBasis.isFloor() ? 1.5 : 1.35,
            entryBasis.isFloor() ? PortalBasis.FUNNEL_HEIGHT : 1.5,
            entryBasis.isFloor() ? 1.5 : 1.35
        );
        List<Entity> candidates = serverLevel.getEntities(this, scanBox, this::canTeleportEntity);
        long now = serverLevel.getGameTime();

        for (Entity entity : candidates) {
            if (isOnCooldown(entity.getUUID(), now)) {
                continue;
            }
            // Compute TRUE per-tick velocity from position delta (essential for ServerPlayer,
            // whose server-side deltaMovement is stale because player movement is client-authoritative!)
            Vec3 trueVel = computeTrueVelocity(entity, now);
            if (trueVel.lengthSqr() > 0.01) {
                RECENT_VELOCITY.put(entity.getUUID(), trueVel);
                RECENT_VELOCITY_TICK.put(entity.getUUID(), now);
            }
            if (entryBasis.isFloor()) {
                applyFloorFunneling(entity, entryBasis);
            }
            if (shouldTeleport(entity, entryBasis, trueVel)) {
                teleportEntity(serverLevel, entity, entryBasis, partnerEndpoint, trueVel, now);
            }
        }
    }

    private static Vec3 computeTrueVelocity(Entity entity, long now) {
        Vec3 curPos = entity.position();
        Vec3 prevPos = LAST_ENTITY_POS.put(entity.getUUID(), curPos);
        Long prevTick = LAST_ENTITY_POS_TICK.put(entity.getUUID(), now);
        Vec3 deltaVel = entity.getDeltaMovement();

        if (prevPos != null && prevTick != null && now > prevTick && (now - prevTick) <= 3L) {
            Vec3 posDelta = curPos.subtract(prevPos).scale(1.0 / (now - prevTick));
            // Ignore huge jumps (teleports)
            if (posDelta.lengthSqr() < 25.0) {
                if (entity instanceof ServerPlayer) {
                    return posDelta;
                }
                if (posDelta.lengthSqr() > deltaVel.lengthSqr()) {
                    return posDelta;
                }
            }
        }
        return deltaVel;
    }

    private boolean isRegisteredInWorldData(PortalWorldData data) {
        if (this.ownerUuid == null) {
            return true;
        }
        for (int ch = 0; ch < PlayerPortalRegistry.MAX_CHANNELS; ch++) {
            var pairOpt = data.getPair(this.ownerUuid, ch);
            if (pairOpt.isPresent()) {
                var pair = pairOpt.get();
                if (this.getUUID().equals(pair.primaryId()) || this.getUUID().equals(pair.secondaryId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBackingSurfaceIntact(ServerLevel level) {
        PortalBasis basis = getPortalBasis();
        Vec3 behindCenter = this.position().subtract(basis.forward().scale(0.15));
        BlockPos backingBlock = BlockPos.containing(behindCenter);
        return PortalSurfaceRules.isBlockPortalable(level, backingBlock, basis.facing());
    }

    public void fizzleAndClose(ServerLevel level) {
        PortalBasis basis = getPortalBasis();
        DustParticleOptions dust = new DustParticleOptions(getColorRgb(), 1.2f);
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2.0 * i) / 16.0;
            Vec3 p = this.position()
                .add(basis.right().scale(Math.cos(angle) * PortalBasis.HALF_WIDTH))
                .add(basis.up().scale(Math.sin(angle) * PortalBasis.HALF_HEIGHT))
                .add(basis.forward().scale(0.08));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
        }
        level.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.4f);

        Optional<UUID> partnerId = PortalWorldData.get(level).unregisterPortal(this.getUUID());
        partnerId.ifPresent(id -> {
            Entity e = level.getEntity(id);
            if (e instanceof PortalEntity partner) {
                partner.syncLinkWith(null);
            }
        });
        this.discard();
    }

    private PortalEntity resolvePartner(ServerLevel level) {
        Optional<UUID> partnerId = PortalWorldData.get(level).getPartnerPortal(this.getUUID());
        if (partnerId.isEmpty()) {
            return null;
        }
        Entity entity = level.getEntity(partnerId.get());
        if (entity instanceof PortalEntity partner && partner.isAlive() && partner != this) {
            return partner;
        }
        return null;
    }

    private boolean canTeleportEntity(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof PortalEntity || entity instanceof PortalProjectileEntity) {
            return false;
        }
        // Carried cubes teleport alongside their carrying player
        if (entity instanceof WeightedCubeEntity cube && cube.getCarrierUuid() != null) {
            return false;
        }
        return true;
    }

    private static boolean isOnCooldown(UUID entityId, long now) {
        Long until = TELEPORT_COOLDOWN_UNTIL.get(entityId);
        return until != null && now < until;
    }

    /**
     * Portal 1 sv_player_funnel_into_portals: gently pulls falling entities horizontally
     * toward the center of a floor portal so flings feel accurate and smooth.
     */
    private void applyFloorFunneling(Entity entity, PortalBasis basis) {
        Vec3 vel = entity.getDeltaMovement();
        if (vel.y >= -0.18) {
            return;
        }
        Vec3 offset = entity.position().subtract(this.position());
        double heightAbove = offset.y;
        if (heightAbove <= 0.1 || heightAbove > PortalBasis.FUNNEL_HEIGHT) {
            return;
        }
        double dx = this.getX() - entity.getX();
        double dz = this.getZ() - entity.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 0.08 && horizDist < PortalBasis.FUNNEL_RADIUS) {
            double pull = 0.065;
            entity.setDeltaMovement(vel.add((dx / horizDist) * pull, 0.0, (dz / horizDist) * pull));
        }
    }

    private Vec3 getEntitySamplePoint(Entity entity, PortalBasis basis) {
        if (basis.isFloor()) {
            return entity.position().add(0.0, 0.15, 0.0);
        }
        if (basis.isCeiling()) {
            return entity.position().add(0.0, entity.getBbHeight() - 0.1, 0.0);
        }
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    private boolean shouldTeleport(Entity entity, PortalBasis basis, Vec3 vel) {
        Vec3 samplePoint = getEntitySamplePoint(entity, basis);
        Vec3 local = basis.toLocal(samplePoint.subtract(this.position()));

        // Immersive-Portals-style exact plane-crossing check using previous tick position -> current position
        Vec3 prevSample = LAST_SAMPLE_POS.put(entity.getUUID(), samplePoint);
        if (prevSample != null && prevSample.distanceToSqr(samplePoint) < 16.0) {
            Vec3 prevLocal = basis.toLocal(prevSample.subtract(this.position()));
            if (prevLocal.z > 0.05 && local.z <= 0.36) {
                double denom = prevLocal.z - local.z;
                double frac = denom > 1.0e-5 ? Math.max(0.0, Math.min(1.0, (prevLocal.z - 0.1) / denom)) : 0.5;
                Vec3 cross = prevLocal.lerp(local, frac);
                if (basis.isInsideOval(cross, 1.12)) {
                    return true;
                }
            }
        }

        // Predictive swept plane-crossing check: if the entity's next tick crosses the portal plane
        // (e.g. falling fast into a floor portal or walking into a wall portal before solid-block collision zeroes vel),
        // teleport immediately while full momentum is intact!
        Vec3 nextSample = samplePoint.add(vel);
        Vec3 nextLocal = basis.toLocal(nextSample.subtract(this.position()));
        double wallPreTrigger = basis.isWall() ? 0.38 : 0.15;
        if (local.z >= -0.2 && nextLocal.z <= wallPreTrigger) {
            double frac = (local.z - nextLocal.z) > 1.0e-5
                ? Math.max(0.0, Math.min(1.0, local.z / (local.z - nextLocal.z)))
                : 0.5;
            Vec3 crossLocal = local.lerp(nextLocal, frac);
            if (basis.isInsideOval(crossLocal, 1.15)) {
                return true;
            }
        }

        if (!basis.isInsideOval(local, 1.05)) {
            return false;
        }

        double intoSpeed = -vel.dot(basis.forward());

        if (basis.isFloor()) {
            // Stepping or falling onto a floor portal
            return local.z <= 0.50 && local.z >= -0.9 && vel.y <= 0.05;
        }
        if (basis.isCeiling()) {
            // Rising into a ceiling portal
            return local.z <= 0.50 && local.z >= -0.9 && vel.y >= -0.05;
        }
        // Wall portal: close to plane and moving into or pressing against it
        return local.z <= 0.55 && local.z >= -0.85 && intoSpeed >= -0.02;
    }

    private void teleportEntity(
        ServerLevel level,
        Entity entity,
        PortalBasis entryBasis,
        PlayerPortalRegistry.PortalEndpoint exitEndpoint,
        Vec3 trueVel,
        long now
    ) {
        PortalBasis exitBasis = exitEndpoint.basis();
        Vec3 exitPos = exitEndpoint.position();
        Vec3 inVel = trueVel;

        // If Minecraft's block collision just zeroed the entity's velocity on the exact impact frame
        // before PortalEntity.tick() ran, recover the pre-impact velocity from the last 2 ticks.
        Long velTick = RECENT_VELOCITY_TICK.get(entity.getUUID());
        Vec3 recent = RECENT_VELOCITY.get(entity.getUUID());
        if (recent != null && velTick != null && (now - velTick) <= 2L) {
            double curInto = -inVel.dot(entryBasis.forward());
            double recentInto = -recent.dot(entryBasis.forward());
            if (recentInto > curInto + 0.12) {
                inVel = recent;
            }
        }

        // Suppress Minecraft's resting ground-gravity constant (-0.0784) on wall-to-wall portals
        // so walking straight through a wall portal doesn't apply spurious downward drift.
        if (entryBasis.isWall() && exitBasis.isWall() && Math.abs(inVel.y) < 0.10) {
            inVel = new Vec3(inVel.x, 0.0, inVel.z);
        }

        // 1. Exact Portal 1 momentum transformation
        Vec3 transformedVel = PortalTransform.transformVector(inVel, entryBasis, exitBasis);
        Vec3 finalVel = PortalTransform.applyExitVelocityRules(transformedVel, entryBasis, exitBasis);

        // 2. Exit clearance based on exit surface orientation (ensuring feet/head never clip surrounding blocks)
        double exitOffset;
        if (exitBasis.isCeiling()) {
            exitOffset = entity.getBbHeight() + 0.30;
        } else if (exitBasis.isFloor()) {
            exitOffset = 0.25;
        } else {
            exitOffset = Math.max(0.78, entity.getBbWidth() * 0.65 + 0.45);
        }

        Vec3 sampleIn = entryBasis.isFloor()
            ? entity.position()
            : entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        Vec3 targetCenter = PortalTransform.transformPosition(
            sampleIn, this.position(), entryBasis, exitPos, exitBasis, exitOffset
        );
        Vec3 targetFeetPos;
        if (exitBasis.isWall()) {
            // Keep feet cleanly at or above the bottom of the wall portal aperture so we never clip into the floor
            double minFeetY = exitPos.y - PortalBasis.HALF_HEIGHT + 0.05;
            double desiredFeetY = Math.max(minFeetY, targetCenter.y - entity.getBbHeight() * 0.5);
            targetFeetPos = new Vec3(targetCenter.x, desiredFeetY, targetCenter.z);
        } else if (exitBasis.isCeiling()) {
            targetFeetPos = targetCenter.subtract(0.0, entity.getBbHeight(), 0.0);
        } else {
            targetFeetPos = targetCenter;
        }

        // 3. Transform look angles (yaw & pitch)
        PortalTransform.YawPitch newLook = PortalTransform.transformYawPitch(
            entity.getYRot(), entity.getXRot(), entryBasis, exitBasis
        );

        // 4. Mark cooldown and clear stale plane-crossing/velocity history before teleporting
        TELEPORT_COOLDOWN_UNTIL.put(entity.getUUID(), now + COOLDOWN_TICKS);
        LAST_SAMPLE_POS.remove(entity.getUUID());
        LAST_ENTITY_POS.remove(entity.getUUID());
        LAST_ENTITY_POS_TICK.remove(entity.getUUID());

        // 5. Perform teleportation + velocity application (loads ONLY destination chunk on demand,
        //    never permanently force-loading chunks or ticking distant machines)
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(
                level,
                targetFeetPos.x, targetFeetPos.y, targetFeetPos.z,
                EnumSet.noneOf(Relative.class),
                newLook.yaw(), newLook.pitch(),
                false
            );
            player.setDeltaMovement(finalVel);
            player.hurtMarked = true;
            PortalNetworking.sendVelocitySync(player, finalVel.x, finalVel.y, finalVel.z, newLook.yaw(), newLook.pitch());

            // Carry held Weighted Cube through the portal along with the player
            WeightedCubeEntity carried = CarryManager.getCarriedCube(player);
            if (carried != null) {
                TELEPORT_COOLDOWN_UNTIL.put(carried.getUUID(), now + COOLDOWN_TICKS);
                Vec3 cubeDest = player.getEyePosition().add(player.getLookAngle().scale(1.6));
                carried.teleportTo(cubeDest.x, cubeDest.y - WeightedCubeEntity.CUBE_SIZE * 0.5, cubeDest.z);
                carried.setDeltaMovement(finalVel);
                carried.resetFallDistance();
            }
        } else {
            // Ensure destination chunk is briefly accessible for non-player entity placement
            level.getChunk(BlockPos.containing(targetFeetPos));
            entity.teleportTo(targetFeetPos.x, targetFeetPos.y, targetFeetPos.z);
            entity.setYRot(newLook.yaw());
            entity.setXRot(newLook.pitch());
            entity.setDeltaMovement(finalVel);
            entity.hurtMarked = true;
        }

        // 6. Audio & visual cues on entry and exit
        BlockPos exitBlockPos = BlockPos.containing(exitPos);
        level.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.45f, 1.35f);
        level.playSound(null, exitBlockPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.45f, 1.15f);
        spawnPortalBurst(level, this.position(), this.getColorRgb());
        spawnPortalBurst(level, exitPos, exitEndpoint.colorRgb());
    }

    private static void spawnPortalBurst(ServerLevel level, Vec3 pos, int colorRgb) {
        DustParticleOptions dust = new DustParticleOptions(colorRgb, 1.0f);
        level.sendParticles(dust, pos.x, pos.y, pos.z, 10, 0.3, 0.4, 0.3, 0.05);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.getString("OwnerUuid").ifPresent(s -> {
            try {
                this.ownerUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {}
        });
        input.getString("LinkedUuid").ifPresent(s -> {
            try {
                this.linkedPortalUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {}
        });
        Direction dir = Direction.from3DDataValue(input.getIntOr("Facing", Direction.NORTH.get3DDataValue()));
        Vec3 up = new Vec3(
            input.getDoubleOr("UpX", 0.0),
            input.getDoubleOr("UpY", 1.0),
            input.getDoubleOr("UpZ", 0.0)
        );
        setPortalBasis(PortalBasis.fromForwardAndUp(dir, Vec3.atLowerCornerOf(dir.getUnitVec3i()), up));
        this.entityData.set(DATA_IS_SECONDARY, input.getBooleanOr("IsSecondary", false));
        this.entityData.set(DATA_COLOR_RGB, input.getIntOr("ColorRgb", PortalColorScheme.CLASSIC.primaryRgb()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.ownerUuid != null) {
            output.putString("OwnerUuid", this.ownerUuid.toString());
        }
        if (this.linkedPortalUuid != null) {
            output.putString("LinkedUuid", this.linkedPortalUuid.toString());
        }
        PortalBasis basis = getPortalBasis();
        output.putInt("Facing", basis.facing().get3DDataValue());
        output.putDouble("UpX", basis.up().x);
        output.putDouble("UpY", basis.up().y);
        output.putDouble("UpZ", basis.up().z);
        output.putBoolean("IsSecondary", isSecondary());
        output.putInt("ColorRgb", getColorRgb());
    }
}
