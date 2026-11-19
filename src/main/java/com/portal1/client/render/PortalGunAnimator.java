package com.portal1.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * Smooth, framerate-independent first-person procedural animation for the 3D Portal Gun:
 * - Portal 1 firing recoil kickback, upward barrel pitch, and subtle primary/secondary roll snap.
 * - Gentle idle breathing & energy-core hum sway.
 */
public final class PortalGunAnimator {

    private static final long RECOIL_DURATION_MS = 320L;
    private static long lastFireTimeMs = 0L;
    private static boolean lastFireSecondary = false;

    private PortalGunAnimator() {}

    public static void triggerFire(boolean secondary) {
        lastFireTimeMs = System.currentTimeMillis();
        lastFireSecondary = secondary;
    }

    /**
     * Applies smooth idle breathing sway and firing recoil to the first-person PoseStack.
     */
    public static void applyFirstPersonTransform(PoseStack poseStack) {
        long now = System.currentTimeMillis();
        double timeSec = (now % 600_000L) / 1000.0;

        // 1. Subtle idle breathing & energy hum sway
        float swayY = (float) (Math.sin(timeSec * 2.2) * 0.009);
        float swayX = (float) (Math.cos(timeSec * 1.1) * 0.0045);
        float swayRoll = (float) (Math.sin(timeSec * 1.6) * 0.85);

        poseStack.translate(swayX, swayY, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(swayRoll));

        // 2. Crisp Portal 1 firing recoil (kickback + muzzle rise + slight side twist)
        long elapsed = now - lastFireTimeMs;
        if (elapsed >= 0L && elapsed < RECOIL_DURATION_MS) {
            float progress = elapsed / (float) RECOIL_DURATION_MS;
            // Fast punch-in (first 18%), smooth springy recovery (remaining 82%)
            float kick;
            if (progress < 0.18f) {
                float p = progress / 0.18f;
                kick = (float) Math.sin(p * Math.PI * 0.5);
            } else {
                float p = (progress - 0.18f) / 0.82f;
                float inv = 1.0f - p;
                kick = inv * inv;
            }

            float kickZ = 0.15f * kick;
            float kickY = 0.022f * kick;
            float pitchDeg = 11.5f * kick;
            float rollDeg = (lastFireSecondary ? -4.5f : 4.5f) * kick;

            poseStack.translate(0.0f, kickY, kickZ);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rollDeg));
        }
    }
}
