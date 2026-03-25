package com.smoke.client.feature.module.world.scaffold;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Geometry helpers for "legit" scaffold/bridge edge detection.
 *
 * <p>Given a supporting block under the player's feet and a horizontal movement direction,
 * compute how far the player's AABB can still move before its XZ footprint no longer overlaps
 * the support block's top face. This works for arbitrary (including diagonal) directions.</p>
 */
public final class LegitScaffoldGeometry {
    private LegitScaffoldGeometry() {
    }

    /**
     * Returns the maximum non-negative distance (in blocks) the given AABB can be translated along
     * {@code horizontalDirection} before it loses any XZ overlap with the support block.
     *
     * <p>When this value is near 0, the player is at the "absolute edge" (furthest still-supported point).</p>
     */
    public static double remainingSupportDistance(Box playerBox, BlockPos supportBlock, Vec3d horizontalDirection) {
        double dirX = horizontalDirection.x;
        double dirZ = horizontalDirection.z;
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length <= 1.0E-8D) {
            return Double.POSITIVE_INFINITY;
        }

        dirX /= length;
        dirZ /= length;

        double blockMinX = supportBlock.getX();
        double blockMaxX = blockMinX + 1.0D;
        double blockMinZ = supportBlock.getZ();
        double blockMaxZ = blockMinZ + 1.0D;

        double limitX = axisLimit(playerBox.minX, playerBox.maxX, blockMinX, blockMaxX, dirX);
        double limitZ = axisLimit(playerBox.minZ, playerBox.maxZ, blockMinZ, blockMaxZ, dirZ);

        return Math.max(0.0D, Math.min(limitX, limitZ));
    }

    /**
     * True iff the player is within {@code epsilon} blocks of losing any support overlap.
     */
    public static boolean isAtAbsoluteEdge(Box playerBox, BlockPos supportBlock, Vec3d horizontalDirection, double epsilon) {
        return remainingSupportDistance(playerBox, supportBlock, horizontalDirection) <= Math.max(0.0D, epsilon);
    }

    private static double axisLimit(double boxMin, double boxMax, double blockMin, double blockMax, double dir) {
        if (Math.abs(dir) <= 1.0E-8D) {
            return Double.POSITIVE_INFINITY;
        }

        // Overlap exists when: (boxMin + t*dir) < blockMax && (boxMax + t*dir) > blockMin
        // For a fixed direction, the first inequality to break determines the limit.
        if (dir > 0.0D) {
            // boxMin moves toward blockMax; overlap is lost when boxMin >= blockMax
            return (blockMax - boxMin) / dir;
        }

        // dir < 0: boxMax moves toward blockMin; overlap is lost when boxMax <= blockMin
        return (blockMin - boxMax) / dir;
    }
}
