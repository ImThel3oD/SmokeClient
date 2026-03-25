package com.smoke.client.rotation;

import net.minecraft.util.math.MathHelper;

import java.util.Objects;

public final class RotationRequest {
    /**
     * TTL value that indicates a request should be kept active until explicitly released.
     * <p>
     * This is required for modes that must never allow a one-tick drop where vanilla camera
     * rotation would leak into outbound movement packets.
     */
    public static final int TTL_PERSISTENT = 0;

    private final String ownerId;
    private final float targetYaw;
    private final float targetPitch;
    private final int priority;
    private final int ttlTicks;
    private final float maxYawStep;
    private final float maxPitchStep;
    private final RotationMode mode;
    private final boolean movementCorrection;
    private final Float movementYawOverride;
    private final boolean silentRenderRotation;
    private final boolean digitalInputCorrection;
    private final boolean gcd;

    public RotationRequest(
            String ownerId,
            float targetYaw,
            float targetPitch,
            int priority,
            int ttlTicks,
            float maxYawStep,
            float maxPitchStep,
            RotationMode mode,
            boolean movementCorrection
    ) {
        this(
                ownerId,
                targetYaw,
                targetPitch,
                priority,
                ttlTicks,
                maxYawStep,
                maxPitchStep,
                mode,
                movementCorrection,
                null,
                false,
                true,
                true
        );
    }

    public RotationRequest(
            String ownerId,
            float targetYaw,
            float targetPitch,
            int priority,
            int ttlTicks,
            float maxYawStep,
            float maxPitchStep,
            RotationMode mode,
            boolean movementCorrection,
            Float movementYawOverride
    ) {
        this(
                ownerId,
                targetYaw,
                targetPitch,
                priority,
                ttlTicks,
                maxYawStep,
                maxPitchStep,
                mode,
                movementCorrection,
                movementYawOverride,
                false,
                true,
                true
        );
    }

    public RotationRequest(
            String ownerId,
            float targetYaw,
            float targetPitch,
            int priority,
            int ttlTicks,
            float maxYawStep,
            float maxPitchStep,
            RotationMode mode,
            boolean movementCorrection,
            boolean silentRenderRotation
    ) {
        this(
                ownerId,
                targetYaw,
                targetPitch,
                priority,
                ttlTicks,
                maxYawStep,
                maxPitchStep,
                mode,
                movementCorrection,
                null,
                silentRenderRotation,
                true,
                true
        );
    }

    public RotationRequest(
            String ownerId,
            float targetYaw,
            float targetPitch,
            int priority,
            int ttlTicks,
            float maxYawStep,
            float maxPitchStep,
            RotationMode mode,
            boolean movementCorrection,
            Float movementYawOverride,
            boolean silentRenderRotation
    ) {
        this(
                ownerId,
                targetYaw,
                targetPitch,
                priority,
                ttlTicks,
                maxYawStep,
                maxPitchStep,
                mode,
                movementCorrection,
                movementYawOverride,
                silentRenderRotation,
                true,
                true
        );
    }

    public RotationRequest(
            String ownerId,
            float targetYaw,
            float targetPitch,
            int priority,
            int ttlTicks,
            float maxYawStep,
            float maxPitchStep,
            RotationMode mode,
            boolean movementCorrection,
            Float movementYawOverride,
            boolean silentRenderRotation,
            boolean digitalInputCorrection
    ) {
        this(
                ownerId,
                targetYaw,
                targetPitch,
                priority,
                ttlTicks,
                maxYawStep,
                maxPitchStep,
                mode,
                movementCorrection,
                movementYawOverride,
                silentRenderRotation,
                digitalInputCorrection,
                true
        );
    }

    public RotationRequest(
            String ownerId,
            float targetYaw,
            float targetPitch,
            int priority,
            int ttlTicks,
            float maxYawStep,
            float maxPitchStep,
            RotationMode mode,
            boolean movementCorrection,
            Float movementYawOverride,
            boolean silentRenderRotation,
            boolean digitalInputCorrection,
            boolean gcd
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.targetYaw = targetYaw;
        this.targetPitch = MathHelper.clamp(targetPitch, -90.0F, 90.0F);
        this.priority = priority;
        this.ttlTicks = ttlTicks;
        this.maxYawStep = maxYawStep <= 0.0F ? Float.MAX_VALUE : maxYawStep;
        this.maxPitchStep = maxPitchStep <= 0.0F ? Float.MAX_VALUE : maxPitchStep;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.movementCorrection = movementCorrection;
        this.movementYawOverride = movementYawOverride;
        this.silentRenderRotation = silentRenderRotation;
        this.digitalInputCorrection = digitalInputCorrection;
        this.gcd = gcd;
    }

    public String ownerId() {
        return ownerId;
    }

    public float targetYaw() {
        return targetYaw;
    }

    public float targetPitch() {
        return targetPitch;
    }

    public int priority() {
        return priority;
    }

    public int ttlTicks() {
        return ttlTicks;
    }

    public boolean persistent() {
        return ttlTicks <= 0;
    }

    public float maxYawStep() {
        return maxYawStep;
    }

    public float maxPitchStep() {
        return maxPitchStep;
    }

    public RotationMode mode() {
        return mode;
    }

    public boolean movementCorrection() {
        return movementCorrection;
    }

    /**
     * Optional: when movement correction is enabled, use this yaw as the "intended movement"
     * direction instead of the live camera yaw. This allows modules to keep camera fully free
     * while forcing movement along a fixed direction.
     */
    public Float movementYawOverride() {
        return movementYawOverride;
    }

    /**
     * Optional: when using silent rotations, align the local third-person model's head/body yaw to the
     * packet rotation. This does not affect camera yaw/pitch.
     */
    public boolean silentRenderRotation() {
        return silentRenderRotation;
    }

    /**
     * Optional: when movement correction is enabled, also rewrite the digital forward/back/left/right bits.
     * Disabling this keeps raw WASD input unchanged while still correcting the analog movement vector.
     */
    public boolean digitalInputCorrection() {
        return digitalInputCorrection;
    }

    public boolean gcd() {
        return gcd;
    }
}
