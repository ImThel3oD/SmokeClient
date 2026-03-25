package com.smoke.client.rotation;

public record RotationFrame(
        boolean active,
        String ownerId,
        float clientYaw,
        float clientPitch,
        float movementYaw,
        boolean movementYawOverridden,
        float targetYaw,
        float targetPitch,
        float packetYaw,
        float packetPitch,
        boolean applyPacketRotation,
        boolean applyVisibleRotation,
        boolean applyMovementCorrection,
        boolean applySilentRenderRotation,
        boolean applyDigitalInputCorrection
) {
    public static RotationFrame inactive() {
        return new RotationFrame(false, "", 0.0F, 0.0F, 0.0F, false, 0.0F, 0.0F, 0.0F, 0.0F, false, false, false, false, false);
    }
}
