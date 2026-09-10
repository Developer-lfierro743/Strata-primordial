# Strata Primordial

**Original voxel sandbox** — Kotlin Multiplatform + SDL3 + Vulkan + ECS.

Not a Minecraft clone. Built from scratch with modern architecture.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.4.20 (KMP) |
| Graphics | Vulkan 1.4 (vulkan-kotlin) |
| Windowing | SDL3 |
| ECS | Custom archetype-free ECS |
| Architecture | Monolith native targets |

## Targets

- `desktop` — Windows (mingwX64)
- `linuxX64` — Linux x86_64
- `linuxArm64` — Linux ARM64 (Android via chroot)

## Build

```bash
./gradlew :linkDebugExecutableLinuxX64   # Linux x64
./gradlew :linkDebugExecutableLinuxArm64 # Linux ARM64 (cross-compile)
./gradlew :linkDebugExecutableDesktop    # Windows (native or cross)
```

## Architecture

```
src/
├── commonMain/           # Shared game logic
│   └── kotlin/strata/
│       ├── ecs/          # Entity Component System
│       ├── math/         # Vec3, Mat4, AABB
│       ├── world/        # Block, Chunk, World
│       ├── game/         # Game class + Systems
│       └── ...
├── nativeMain/           # Native platform layer
│   └── kotlin/strata/
│       ├── platform/     # SDL3 entry point
│       ├── vulkan/       # Vulkan types
│       └── renderer/     # Chunk mesher
└── nativeInterop/        # C interop definitions
    └── cinterop/
        └── sdl3.def      # SDL3 cinterop
```

## Roadmap

- [x] Project structure & build system
- [x] ECS with atomic operations
- [x] Chunk-based world (16³ chunks, 1D array)
- [x] SDL3 window + event loop
- [x] Chunk mesher with face culling
- [ ] Vulkan renderer (WIP)
- [ ] Infinite world generation (perlin/simplex)
- [ ] Player physics & collision
- [ ] Block placing/breaking
- [ ] SafetyGuardian (security kernel)

## License

All rights reserved. Strata is proprietary software by Novusforge Studios.

---

Built with Kotlin Multiplatform. No Mojang code used. No Minecraft assets included.
