# Scaffold `LEGIT` mode (Smoke 1.21.8 Fabric)

This document describes the **Legit bridging mode** implemented in the Scaffold module. The goal is to produce a packet stream that looks like clean backward-bridging: **server-side yaw/pitch stay locked** while the **client camera remains free**, with **frame-correct edge sneaking**, **PRE-phase placement**, and **jump/vertical scaffold continuity**.

## Non‑negotiable invariants

1. **Server rotation never snaps to the camera** while the module is enabled.
2. **Client camera is never moved** by the module (mouse input stays fully responsive).
3. **Every placement is backed by a valid raycast hit** computed from the **locked server rotation** (no blind placements).

## Rotation lock design (RotationService integration)

- **Persistent hold:** Scaffold submits a `RotationRequest` using `ttlTicks = RotationRequest.TTL_PERSISTENT` and re-submits every `TickEvent.PRE` anyway.
  - This ensures there is never a “missed tick → TTL expired → packet yaw/pitch revert to camera” window.
- **Silent + sticky:** uses `RotationMode.SILENT_STICKY`.
  - Silent: camera yaw/pitch are untouched (RotationService only aligns head/body yaw).
  - Sticky: RotationService continuity is maintained across brief request churn.
- **Movement correction:** `movementCorrection=true`.
- **Fixed movement axis while camera is free:** `movementYawOverride = bridgeYaw` is supplied in the request, so movement correction uses the captured bridge direction even if the user moves their mouse.

Implementation:
- `src/main/java/com/smoke/client/feature/module/world/ScaffoldModule.java` (`LEGIT` path)
- `src/main/java/com/smoke/client/rotation/RotationService.java` packet injection + movement correction

## Movement correction math (why W works while server yaw faces backward)

- The locked server yaw is `lockedYaw = bridgeYaw + 180°` (server sees the player looking backward).
- RotationService remaps the movement vector by:
  - `yawDelta = movementYaw - packetYaw` (in this mode, `movementYaw` is fixed to `bridgeYaw`)
  - movement vector is rotated by `yawDelta` so that pressing W still moves the player forward along the captured bridge direction even though the server-facing yaw is reversed.

## Tick ordering (why pitch planning is “one tick early”)

Smoke tick start order is:

1. `rotationService.beginTick(player)` (publishes current applied frame)
2. `TickEvent.PRE` (modules run here)
3. `rotationService.refresh(player)` (steps toward submitted targets)
4. Vanilla continues and sends outbound packets later in the tick

`PlayerInteractBlockC2SPacket` does not carry look. Server-side reach/LOS checks use the **most recently received move packet** rotation, so if pitch changes and placement happen “same PRE”, the server may still be using the previous pitch.

Therefore, Legit mode:
- **Places in PRE** using the **already-applied** `RotationFrame.packetYaw/packetPitch`.
- **Plans pitch for the next tick** (using a predicted eye position), then submits that target pitch so it becomes applied before the next PRE placement attempt.

## Pitch calculation (raycast-driven, yaw-locked)

Legit mode treats pitch as “owned by the module” (never derived from camera), and chooses pitch values by scanning a bounded interval and raycasting with **locked yaw**:

- Scan `pitch ∈ [55°, 89°]` (downward)
- Perform a block raycast from the predicted next-tick eye position
- Accept a pitch only if the resulting `placePos = hitPos.offset(hitSide)` is:
  - replaceable, and
  - matches the **current intent**:
    - **Forward/bridge intent** (edge sneak active): horizontal placement in the captured bridge direction, same Y layer as underfoot
    - **Underfoot intent** (airborne + underfoot target replaceable): `placePos == predictedUnderfoot`
- Choose the candidate pitch with smallest `|pitch - currentDesiredPitch|` (stability), tie-break by closest hit distance.

## Edge detection (absolute edge, diagonal-safe)

Edge timing uses exact AABB overlap math:

- Pick the support block under the player’s feet but slightly “behind” along the bridge direction.
- Compute remaining distance until the player’s AABB footprint would stop overlapping the support block:
  - `LegitScaffoldGeometry.remainingSupportDistance(playerBox, supportBlock, bridgeDir)`
- Engage sneak when `remaining <= estimatedStep + epsilon` **and** there is a floor gap ahead.

This avoids “front corner unsupported” heuristics that sneak too early (especially on diagonals).

Implementation:
- `src/main/java/com/smoke/client/feature/module/world/scaffold/LegitScaffoldGeometry.java`

## Sneak timing (frame-correct + prompt release)

Horizontal cycle:

Tick N (approach):
- Condition: would step past support this tick → `forceSneak = true`
- Vanilla sneak edge detection clamps movement to the absolute edge.
- Pitch is planned for Tick N+1 edge placement (edge‑predicted eye position).

Tick N+1 (placement):
- PRE: raycast using **already-applied** packet yaw/pitch → send placement if valid
- Immediately after placement success in PRE: `forceSneak = false`
- MovementInputEvent later the same tick sees `forceSneak=false` → sneak is released promptly; player walks at full speed onto the new block.

## Placement pipeline (PRE-only, no blind packets)

On each PRE placement attempt:

1. Read `RotationFrame` and require ownership (`frame.ownerId == scaffold.id`).
2. Raycast from `player.getEyePos()` using `frame.packetYaw/packetPitch`.
3. Require `HitResult.Type.BLOCK`.
4. Compute `placePos = hit.getBlockPos().offset(hit.getSide())`.
5. Require `placePos` replaceable.
6. Require intent match (forward bridge vs underfoot).
7. Switch to a placeable hotbar block (optional auto-switch) and call `interactionManager.interactBlock(...)`.

## Jump / vertical scaffold support

- Jump detection arms when `onGround → !onGround` with upward velocity.
- While airborne, the module prioritizes **underfoot** placement whenever `player.getBlockPos().down()` is replaceable.
- Pitch is planned using predicted next-tick eye position so the underfoot placement can happen at the earliest valid tick during the jump arc.

## Sprint interaction

Legit mode disables sprint in `MovementInputEvent` to keep step distance consistent and avoid sprint→sneak timing drift.

## Module lifecycle + failure paths

Enable:
- Capture `bridgeYaw` from camera yaw.
- Set `lockedYaw = bridgeYaw + 180°`.
- Submit a persistent silent rotation request immediately.

Disable:
- Release rotation request (`RotationService.release(id)`).
- Clear forced sneak state.

No blocks in hotbar:
- Rotation lock remains active.
- Placement attempts are skipped.
- One-time chat feedback is emitted.

## Packet sequence audit (expected)

### Horizontal: 3 placements (conceptual)

For each tick, the key outgoing packets are:

- `PlayerMoveC2SPacket*` (every tick): yaw=`lockedYaw`, pitch=`lockedPitch` (RotationService rewrite)
- Sneak toggles (only when state changes): `ClientCommandC2SPacket` / `PlayerCommandC2SPacket` start/stop sneaking (vanilla)
- Placement tick: `PlayerInteractBlockC2SPacket` (from `interactBlock(...)`)

Cycle example:

- Tick A (approach): move packet with locked yaw/pitch; send START_SNEAKING; no placement
- Tick B (place): PRE sends interact packet; later in tick send STOP_SNEAKING; move packet still locked yaw/pitch
- Tick C..E: walking forward on new block, move packets only
- Repeat for the next block

### Jump: 1 underfoot placement (conceptual)

- Tick J (takeoff): move packet locked yaw/pitch
- Tick J+1..: while airborne, first tick where `underfoot` is replaceable **and** raycast from locked rotation hits a support face producing `placePos == underfoot` → send interact packet in PRE
- Move packets remain locked yaw/pitch for every tick of the arc

## Rejected designs (why they fail)

- **POST-phase placement:** delays placement and/or depends on same-tick rotation changes; explicitly disallowed.
- **Non-persistent rotation ownership (TTL-only):** any skipped submit can leak camera yaw/pitch into move packets.
- **Edge detection via “front corners unsupported”:** sneaks too early (especially diagonals) and slows bridging.
- **Sneak release delayed until the next tick:** causes crawling; violates “release immediately after placement”.
- **Jump detection based only on jump key:** breaks when another module triggers the jump.
- **Blind placements:** sending interact packets without a raycast hit from the locked rotation.

