# Smoke Architecture

## Goals
- Keep the composition root small.
- Keep module authoring cheap.
- Keep rotation and packet ownership single-sourced.
- Keep config stable under renames.
- Keep mixins seam-based, never feature-based.

## Lessons From Existing Clients
- `VenomClient`: too much runtime policy inside bootstrap, module manager, and mixins.
- `Xyno`: best config/event baseline, but still drifts into large runtime and rotation classes.
- `Venom V2 1.8`: strong protocol/correction separation idea, but repo/package hygiene is poor.

## Core Rules
- `SmokeClient` only bootstraps `ClientRuntime`.
- `ModuleManager` only owns registration and lifecycle.
- `Module` stays small: metadata, settings, enable/disable hooks.
- Modules subscribe through the event bus instead of growing one override per seam.
- Packet events are mutable and cancellable.
- Rotation requests go through `RotationService`; modules do not rewrite movement packets directly.
- Mixins delegate to domain services immediately and do not know concrete modules.
- Config keys use stable ids, not display names.

## Package Intent
- `bootstrap/`: composition root and lifecycle wiring
- `event/`: event bus and event types
- `module/`: module lifecycle and metadata
- `setting/`: typed settings that are config-safe and UI-bindable
- `config/`: persistence
- `input/`: debounced key handling
- `command/`: parsing and dispatch
- `rotation/`: request arbitration and resolved frame
- `network/`: inbound/outbound packet seam
- `feature/module/...`: concrete gameplay features
- `ui/`: click GUI, HUD, theme
- `mixin/network`: transport seam only

## Adding A Module
1. Create a module class under `feature/module/<category>/`.
2. Define settings in the constructor.
3. Add `@Subscribe` handlers for the events it needs.
4. Request rotations through `context().rotation().submit(...)` if needed.
5. Register it in the category registrar.

If adding the module requires editing config code, input code, command code, or a mixin, the architecture is being bypassed.

## Mixins
- Reuse existing seam mixins whenever possible.
- New mixins must be justified by a genuinely new game seam.
- Do not call module classes from mixins.

## Build Workflow
- `./gradlew.bat build`
- `./gradlew.bat runClient`
- IntelliJ can use the shared `Minecraft Client` run configuration.
