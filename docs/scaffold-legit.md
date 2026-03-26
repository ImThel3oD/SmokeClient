# Scaffold LEGIT Mode

This document describes the `LEGIT` mode implemented in `ScaffoldModule`.

## Goals

- Keep server-facing yaw and pitch controlled by the module.
- Leave the local camera free so mouse input still feels normal.
- Only place blocks from valid raycasts produced by the locked server rotation.

## Core Behavior

- The module submits a persistent `RotationRequest` every `TickEvent.PRE`.
- `RotationMode.SILENT_STICKY` is used so the camera stays free while packet rotation stays stable.
- Movement correction is enabled so bridge movement still follows the captured bridge direction.
- Placement happens in `PRE` using the already-applied packet rotation from the current frame.

## Why Pitch Is Planned Early

`PlayerInteractBlockC2SPacket` does not carry look data. Server-side validation uses the most recent move packet rotation, so the module plans the next pitch before the next placement attempt instead of trying to rotate and place in the same instant.

## Placement Rules

- A placement must come from a valid raycast.
- The resulting place position must be replaceable.
- Forward bridge placements must match the current bridge direction.
- Underfoot placements are preferred while airborne if the underfoot block is replaceable.

## Sneak Timing

- Sneak is enabled only when support distance and movement intent say the player is about to step past the current support block.
- Sneak is released immediately after a successful placement so movement returns to full speed on the new block.

## Rotation Ownership

- `RotationService` owns the packet-facing yaw and pitch.
- The module should not inject its own look packets for LEGIT mode.
- If this mode needs new behavior, extend the shared rotation flow instead of creating a private packet path.

## Related Files

- `src/main/java/com/smoke/client/feature/module/world/ScaffoldModule.java`
- `src/main/java/com/smoke/client/feature/module/world/scaffold/LegitScaffoldGeometry.java`
- `src/main/java/com/smoke/client/feature/module/world/scaffold/LegitScaffoldInput.java`
- `src/main/java/com/smoke/client/rotation/RotationService.java`
