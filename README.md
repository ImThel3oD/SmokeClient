# Smoke

Smoke is a Fabric client for Minecraft `1.21.8` with an emphasis on maintainable internal systems:

- stable setting IDs and JSON config
- an event-driven module model
- centralized rotation and packet rewriting services
- click GUI and HUD tooling

## Status

This repository currently builds and packages cleanly with Gradle, but it is still an actively evolving codebase. The architectural rules in [ARCHITECTURE.md](./ARCHITECTURE.md) are intended to be enforced by code, not treated as aspirational documentation.

## Build

Windows:

```powershell
.\gradlew.bat build
```

The build output goes to the default Gradle `build/` directory unless `smoke.buildDir` or `SMOKE_BUILD_DIR` is set explicitly.

## Run

```powershell
.\gradlew.bat runClient
```

## Repository Notes

- [ARCHITECTURE.md](./ARCHITECTURE.md): composition and boundary rules
- [docs/scaffold-legit.md](./docs/scaffold-legit.md): Scaffold LEGIT mode notes

## Quality Bar

Before merging changes:

- keep mixins seam-based
- prefer events/services over direct module lookups
- use stable snake_case config IDs
- route packet and rotation behavior through shared services when possible
- run `.\gradlew.bat build`
