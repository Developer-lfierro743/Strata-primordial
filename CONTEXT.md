# Strata Primordial — Universal Development Context

> **Version:** 1.0.0
> **Last Updated:** 2026-08-05 23:17 CDT
> **Project Status:** v0.2.0 Debug Build Running | Release Build Compiling | Monaco Editor Integration In Progress

---

## 📖 TABLE OF CONTENTS

1. [Project Overview](#project-overview)
2. [Tech Stack & Dependencies](#tech-stack--dependencies)
3. [Architecture & File Structure](#architecture--file-structure)
4. [Completed Features](#completed-features)
5. [In Progress](#in-progress)
6. [Build Instructions](#build-instructions)
7. [Security Architecture](#security-architecture)
8. [Agent Notes](#agent-notes)
9. [Known Constraints](#known-constraints)

---

## 🎯 PROJECT OVERVIEW

**Strata Primordial** is a next-generation voxel game engine built from scratch in Kotlin Multiplatform with native Vulkan rendering. It is NOT a Minecraft clone — it is an original game with its own identity, security system, and modding platform.

### Core Identity
- **Name:** Strata Primordial
- **Studio:** Novusforge Studios
- **Version:** 0.2.0
- **License:** Copyright Novusforge Studios. Do not Distribute!

### Vision Statement
A secure, high-performance voxel engine that outperforms legacy Java-based games through:
1. **Native compilation** (Kotlin/Native → mingwX64 Windows executable)
2. **Modern graphics** (Vulkan API, custom shaders, no OpenGL dependency)
3. **Built-in security** (SafetyGuardian kernel, IIV identity, ZKP verification)
4. **Integrated tooling** (Monaco Editor for native mod development)
5. **Multi-threaded architecture** (coroutine workers for world generation + meshing)

---

## 🔧 TECH STACK & DEPENDENCIES

### Language & Runtime
| Component | Version/Location |
|-----------|-----------------|
| Language | Kotlin Multiplatform (KMP) |
| Target | `mingwX64` (Windows x64 native) |
| JDK | JDK 25 |
| Gradle | 9.6.1 |
| Runtime | Kotlin/Native (no JVM required at runtime) |

### Graphics & Rendering
| Component | Version/Location |
|-----------|-----------------|
| API | Vulkan 1.3 |
| Library | `vulkan-kotlin` 1.4.350-1 |
| Allocator | Vulkan Memory Allocator (VMA) |
| Location | `C:\Users\luis\Dev\VulkanMemoryAllocator-master` |
| Math Lib | cglm |
| Location | `C:\Users\luis\Dev\cglm-master\cglm-master` |

### Platform & I/O
| Component | Version/Location |
|-----------|-----------------|
| Window/Input | SDL3 |
| Location | `C:\Users\luis\Dev\SDL-main` |
| Header Path | `include/` |
| Library Path | `build-vs/Release/SDL3.lib` |
| IMGUI Bridge | cimgui (compiling now via Freebuff) |
| ImGui Source | `C:\Users\rui\Dev\imgui-master` |

### Build System
- **Gradle Kotlin DSL** (`build.gradle.kts`)
- **Custom tasks**: `packageDist` for release packaging
- **Output**: Self-contained executable + SDL3.dll in `build/dist/strata/`

---

## 📁 ARCHITECTURE & FILE STRUCTURE

### Project Root
```
C:\Users\luis\Dev\Strata\
├── context.md              # Legacy (use CONTEXT.md instead)
├── CONTEXT.md              # THIS FILE - Universal documentation
├── README.md               # User-facing readme
├── build.gradle.kts        # KMP build configuration
├── settings.gradle.kts     # Plugin/repository config
├── gradle.properties       # Gradle settings
├── src/
│   ├── commonMain/kotlin/strata/     # Core game logic (platform-independent)
│   ├── commonTest/kotlin/strata/     # Unit tests
│   ├── desktopMain/kotlin/strata/    # Desktop entry point
│   └── nativeInterop/cinterop/       # Pure C wrappers for SDL3
├── tools/
│   └── regen_shaders.py           # Shader recompilation utility
├── docs/
│   ├── SECURITY_ARCHITECTURE.md   # Security design document
│   └── JVM_TO_KMP_MAPPING.md      # Migration guide
└── superformula(all 4 parts)/     # Vision documents
```

### Core Source Files (`src/commonMain/kotlin/strata/`)

#### Engine Core
- `VoxelWorld.kt` — Chunk 32³ storage, BlockId enum, raycasting
- `World.kt` — Infinite streamed world management
- `WorldStreamer.kt` — Coroutine multi-worker chunk streaming
- `ChunkMesher.kt` — 6-directional face-culling mesh generator
- `WorldMeshSplit.kt` — Solid/water material split + per-chunk ranges
- `Terrain.kt` — Seeded 2D simplex noise terrain generation
- `FastNoiseLite.kt` — Advanced Perlin-style noise (NEW)

#### Math & Rendering
- `CameraMath.kt` — Camera class, Mat4 matrix, MVP computation
- `Frustum.kt` — Frustum culling via Gribb-Hartmann plane extraction
- `Ui.kt` — Texture-free clip-space quad renderer + 5×7 pixel font

#### Gameplay Systems
- `Player.kt` — Player physics, AABB collision, gravity, swimming
- `Survival.kt` — Health/hunger/thirst/XP simulation
- `GameMode.kt` — SURVIVAL / CREATIVE / SPECTATOR modes
- `Inventory.kt` — PlayerInventory (11 hotbar + 50 storage)
- `InventoryScreen.kt` — Inventory overlay UI

#### UI Screens
- `Screen.kt` — ScreenManager enum (TITLE/GAME/INVENTORY)
- `TitleScreen.kt` — Animated starfield title menu
- `NativeModsScreen.kt` — Native mods hub (WIP)
- `ModListScreen.kt` — Mod management UI (NEW)
- `Platform.kt` — Platform detection utilities (NEW)

#### Chat System
- `Chat.kt` — ChatLog with history recall
- `ChatCommands.kt` — Command registry (/gamemode, /help, /seed, /clear)
- `ChatOverlay.kt` — Chat UI with autocomplete dropdown
- `Hud.kt` — Full HUD rendering (bars, hotbar, minimap, compass, info panel)

#### Security
- `security/SafetyGuardian.kt` — Core security system
- `security/SecurityGateway.kt` — Main security interface
- `security/ChatSecurityBridge.kt` — Chat monitoring integration
- `security/AntiInjection.kt` — Code injection prevention
- `security/ReputationManager.kt` — Player behavior tracking
- `security/Shredder.kt` — Secure file deletion
- `security/crypto/` — AES-256-GCM encryption modules
- `security/iiv/` — Identity Intent Verification
- `security/policy/` — Rule policies
- `security/protocol/` — Security protocols
- `security/memory/` — Memory security

### Shaders (`src/shaders/`)
```
voxel.vert / voxel.frag          # World rendering shader
water.vert / water.frag          # Water shader (animated waves + fresnel)
*.spv                            # Compiled SPIR-V binaries
*_hex.txt / *_uint32.txt         # Embedded shader data
```

---

## ✅ COMPLETED FEATURES

### Engine & Rendering
- [x] Vulkan instance/device/swapchain creation
- [x] Three pipelines: opaque world, transparent water, UI overlay
- [x] Push constants for MVP matrix + camera position + time uniform
- [x] 6-directional face-culling chunk mesher
- [x] Cross-chunk border culling (seamless terrain across chunks)
- [x] Per-chunk frustum culling (only draws visible chunks)
- [x] GPU hardening for iGPUs (Intel Xe/Xe-LP, TBIMR)
- [x] Triple-buffered swapchain with surface pre-transform fix
- [x] TDR-safe inline screenshot capture (F2 key)
- [x] Per-frame semaphore ring (3 pairs) for spec compliance

### World Generation
- [x] Seeded 2D simplex noise terrain (Gustavson-style)
- [x] Sea-level water system (sunken surface plane)
- [x] Sandy beach transitions at shorelines
- [x] Deterministic, seedable world generation
- [x] FastNoiseLite integration for advanced noise patterns

### Streaming & Concurrency
- [x] Multi-threaded chunk streaming (2 worldgen workers + 3 meshing workers)
- [x] Epoch-based dirty tracking (rebuild storm prevention)
- [x] Async whole-world mesh rebuilds (no render-thread stalls)
- [x] WorldMeshSplit separating solid vs water vertex buffers
- [x] Vulkan memory management via VMA

### Player Controller
- [x] WASD movement + mouse look (relative cursor lock)
- [x] Full AABB voxel collision resolution
- [x] Gravity (24 f/s²) with max fall speed cap
- [x] Variable jump height (tap = short hop, hold = full jump)
- [x] Jump buffering (0.15s grace period)
- [x] Coyote time (0.12s after ledge drop)
- [x] Swimming physics (buoyancy, reduced gravity, swim-up on jump)
- [x] Drowning/starvation/dehydration damage
- [x] Block break/place via raycasting

### Survival System
- [x] Double hearts (40 HP = 20 hearts)
- [x] Hunger bar (drains every 30s)
- [x] Thirst bar (drains every 18s)
- [x] XP/leveling system (grow with each level)
- [x] Regen only when fed AND hydrated
- [x] Health floor at 1 (no death, gentle sim)

### Game Modes
- [x] SURVIVAL: Normal physics, hunger/thirst, XP
- [x] CREATIVE: Flight (no gravity), immortal, block interaction
- [x] SPECTATOR: No-clip through everything, no interaction, hidden HUD

### HUD & UI
- [x] Crosshair (hidden in spectator mode)
- [x] Compass with N/E/S/W labels
- [x] Minimap with height-based coloring (sea/beach/grass/stone)
- [x] Animated health/hunger/thirst bars with ghost trails
- [x] 11-slot hotbar with isometric 3D cube icons
- [x] XP bar + level badge
- [x] F3-style info panel (coords, FPS, seed, chunks, mode, facing)
- [x] GPU name + Vulkan version + CPU cores/RAM + OS/power state
- [x] Texture-free rendering (built-in 5×7 pixel font)
- [x] Slot selection pop animation
- [x] Animated starfield background (110 twinkling stars)

### Inventory
- [x] 11-slot hotbar (keys 1-9, 0, -)
- [x] 50-slot storage grid (10×5) opened with E
- [x] Click-to-swap between hotbar and storage
- [x] Starter items initialized in constructor (grass, dirt, stone, wood, sand, water, food, drinks)
- [x] Food/drinks restore hunger/thirst

### Chat & Commands
- [x] Chat log with capped lines (maxLines=60, maxHistory=50)
- [x] History recall (Up/Down arrows)
- [x] Live command autocomplete dropdown (Minecraft-style)
- [x] Argument suggestions with Tab/↑/↓ navigation
- [x] `/gamemode <survival|creative|spectator>` (aliases: s, c, sp)
- [x] `/help` — lists all commands
- [x] `/seed` — shows world seed
- [x] `/clear` — clears chat log

### Security Architecture (SafetyGuardian)
- [x] Cryptographic identity system (IIV - Identity Intent Verification)
- [x] Zero-knowledge proof age verification (ZKP)
- [x] Mod verification scanner (5-layer: signature + content + behavior + plagiarism + classification)
- [x] Anti-injection protection
- [x] Reputation management system
- [x] Crypto module (AES-256-GCM with rotating keys)
- [x] Policy and protocol systems
- [x] Memory security (Shredder for secure deletion)
- [x] Security kernel (Ring 0/1/2/3 isolation)

### Testing
- [x] 17+ unit test suites covering all core modules
- [x] VoxelWorldTest — Chunk generation, raycasting, block ops
- [x] ChunkMesherTest — Face-culling vertex reduction
- [x] TerrainTest — Simplex noise, sea level generation
- [x] WorldTest / WorldMeshSplitTest — Streaming + solid/water split
- [x] PlayerCollisionTest — AABB collision, movement, jump feel
- [x] SurvivalTest — Health/hunger/thirst/XP simulation
- [x] HudTest / HudGeometryTest — HUD state + rendering
- [x] ChatCommandsTest — Command parsing + autocomplete
- [x] GameModeTest — Mode switching + aliases
- [x] InventoryTest / InventoryScreenTest — Inventory management
- [x] TitleScreenTest — Menu button detection
- [x] FrustumTest — Frustum culling calculations
- [x] FastNoiseLiteTest — Noise generation correctness

---

## 🔄 IN PROGRESS

### Freebuff Agent Tasks
- [ ] **Compile cimgui as static library** with SDL3+Vulkan backends
- [ ] **Create .def file** for Kotlin/Native cinterop
- [ ] **Integrate Monaco Editor** into NativeModsScreen.kt
- [ ] **Add ImGui rendering pipeline** inside existing engine renderer
- [ ] **Connect mod editor** to mod management system

### User Tasks
- [ ] Wait for release build compilation (`.\gradlew.bat packageDist`)
- [ ] Test Monaco Editor integration once cimgui is linked
- [ ] Final polish before v0.2.0 release

### Planned Features
- [ ] Textures & lighting (directional sun, per-face AO, sky gradient)
- [ ] Audio (SDL3 audio — footsteps, break/place, ambient)
- [ ] World features (trees, ores: Greenstone/Infernite, caves)
- [ ] Day/night cycle
- [ ] Crafting system (3-tier progression: Basic → Advanced → Overpowered)
- [ ] Save/load persistence
- [ ] THE PURGE event mode (vision document feature)

---

## 🛠️ BUILD INSTRUCTIONS

### Prerequisites
- JDK 25 installed
- Gradle 9.6.1 (bundled with project)
- Vulkan SDK (for shader compilation)
- MinGW g++ (for cimgui compilation)

### Commands (run from project root)

```powershell
# Change to project directory
cd C:\Users\luis\Dev\Strata

# Run all unit tests (REQUIRED before any changes!)
.\gradlew.bat desktopTest

# Build and run debug executable (development)
.\gradlew.bat runDebugExecutableDesktop

# Build optimized release + self-contained distributable
# Output: build/dist/strata/ (exe + SDL3.dll)
.\gradlew.bat packageDist

# Full clean build
.\gradlew.bat clean build
```

### Important Notes
- ⚠️ **Do NOT use PowerShell's `Select-String`** — it's not available in this environment
- ✅ Use `type`, `Get-Content`, or `findstr` instead
- ✅ All changes must pass `.\gradlew.bat desktopTest` before proceeding
- ⚠️ Release build compilation takes time — be patient

---

## 🔐 SECURITY ARCHITECTURE

### SafetyGuardian Kernel

Strata implements a **mini-kernel OS security model for gaming**. Security is not bolted on — it's the foundation.

#### Core Components

| Component | Purpose |
|-----------|---------|
| **IIV** | Cryptographic identity binding (device + account) |
| **ZKP** | Zero-knowledge proofs for age/identity verification |
| **Mod Scanner** | 5-layer mod verification (signature, content, behavior, plagiarism, classification) |
| **AntiInjection** | Prevents code injection attacks |
| **ReputationManager** | Tracks player behavior, flags offenders |
| **Crypto Module** | AES-256-GCM with rotating keys |
| **Shredder** | Secure file deletion (no recovery possible) |

#### Security Layers (Ring Architecture)

```
┌─────────────────────────────────────────┐
│  Ring 0: Security Core                  │
│  • Cryptographic Identity Manager       │
│  • Memory Integrity Checker             │
│  • Process Isolation Enforcer           │
│  • Tamper Detection Engine              │
│  • Audit Log Writer (tamper-proof)      │
├─────────────────────────────────────────┤
│  Ring 1: Game Logic                     │
│  • World Generation                     │
│  • Physics Engine                       │
│  • Entity System                        │
│  • Mod Execution Sandbox                │
├─────────────────────────────────────────┤
│  Ring 2: Rendering (Strata3D)           │
│  • Vulkan Command Buffer Management     │
│  • GPU Memory Management (VMA)          │
│  • Shader Compilation                   │
│  • Frame Synchronization                │
├─────────────────────────────────────────┤
│  Ring 3: User Interface                 │
│  • Chat System                          │
│  • HUD/Menu                             │
│  • Input Handling                       │
│  • Audio                                │
└─────────────────────────────────────────┘
```

#### Compliance Requirements

Strata is designed to comply with **17+ regulations across 50+ countries**:

**Data Protection & Privacy:**
- GDPR (EU/EEA)
- CCPA/CPRA (California, US)
- LGPD (Brazil)
- PIPEDA (Canada)
- POPIA (South Africa)
- PDPA (Singapore/Thailand)
- DPDP Act (India)

**Children's Safety:**
- COPPA (US)
- UK Online Safety Act
- AADC (Australia)
- KOSA (proposed US)

**Content & Safety:**
- DSA (EU)
- NetzDG (Germany)
- China Gaming Regulations
- South Korea Games Act
- Japan JCTA
- Belgium/Netherlands Loot Box Laws

### IIV (Identity Intent Verification)

**What it is:** Cryptographic identity binding that makes ban evasion mathematically impossible.

**How it works:**
1. Player generates Ed25519 keypair
2. Public key sent to IIV server
3. Device fingerprint bound to key
4. Every action signed cryptographically
5. Ban = cryptographic ID revocation (permanent, cross-server)

**Why it beats Minecraft:**
- Minecraft: Account ban (trivially evaded by creating new account)
- Strata: Hardware-bound cryptographic ID (impossible to evade)

### ZKP (Zero-Knowledge Proof) System

**Use cases:**
- Age verification without revealing date of birth
- Identity verification without password transmission
- Mod authorization without private key exposure
- Transaction verification without revealing contents

**Privacy guarantee:** Only mathematical proof transmitted — no personal data stored.

### Mod Verification Pipeline

1. **Signature Verification** (Ed25519 check)
2. **Content Analysis** (ML-based NSFW/malware detection)
3. **Behavior Analysis** (Sandboxed execution monitoring)
4. **Plagiarism Detection** (Code similarity analysis)
5. **Category Classification** (Utility/Content/Cheat/NSFW/Malware)

**Verdicts:** APPROVED / WARNING / BLOCKED / REPORTED

---

## 👥 AGENT NOTES

### For Human Users (Luis)
- Working directory: `C:\Users\luis\Dev\Strata`
- Release build is compiling — wait for completion
- Freebuff is working on cimgui compilation
- Read `docs/SECURITY_ARCHITECTURE.md` for detailed security design

### For AI Agents (Agnes, Freebuff, Hermes, OpenClaw, etc.)

**Shared Context:**
- All agents MUST read `CONTEXT.md` before starting work
- Changes must pass `.\gradlew.bat desktopTest`
- Never modify `strata_sdl3_wrapper.h` without understanding iGPU constraints
- `commonMain` is 100% platform-independent — never import SDL3/Vulkan symbols there

**Freebuff-Specific:**
- Compile cimgui from: `C:\Users\rui\Dev\imgui-master`
- Output: Static library + `.def` file for Kotlin/Native cinterop
- Integrate Monaco Editor into: `src/commonMain/kotlin/strata/NativeModsScreen.kt`
- Use ImGui rendering pipeline inside existing engine renderer

**Agnes-Specific:**
- Focus on core engine, tests, security, gameplay systems
- Coordinate with Freebuff on inter-agent tasks
- Report blockers via `TODO.md` or directly to user

### Inter-Agent Communication

**Method:** File-based communication via `TODO.md` and `CONTEXT.md`

```markdown
## TODO.md Format
- [ ] Task description
- [ ] Status: Not Started | In Progress | Blocked | Complete
- [ ] Assignee: Agent Name
- [ ] Dependencies: Other tasks
- [ ] Notes: Additional context
```

**Update frequency:** Update `CONTEXT.md` when major progress is made.

---

## ⚠️ KNOWN CONSTRAINTS

### Technical Limitations
1. **iGPU Compatibility:** Never use spec-UB synchronization patterns (hangs Intel iGPUs, causes Windows DWM glitches)
2. **PowerShell:** `Select-String` not available — use `type`, `Get-Content`, or `findstr`
3. **Read-only Extensions:** `read_image` only supports PNG/JPEG/GIF/WebP — source code files cannot be read this way
4. **File Access:** Some directories may return "Access denied" (os error 5) due to Windows permissions

### Architecture Rules
1. **Strict Decoupling:** `commonMain` must never import SDL3/Vulkan symbols
2. **Renderer Interface:** No raw C pointers or Vulkan handles cross into `commonMain`
3. **C-Interop Hygiene:** Keep `strata_sdl3_wrapper.h` pure C — no C++ STL headers
4. **Thread Separation:** Worldgen/meshing on coroutine workers, rendering on main thread
5. **Epoch Tracking:** Use epoch/dirty system to prevent rebuild storms

### Performance Considerations
- Use `WorldStreamer` for async chunk loading (never block main thread)
- Chunk meshes are rebuilt only when dirty (epoch tracking)
- Frustum culling skips invisible chunks entirely
- Vertex buffers are triple-buffered to avoid per-frame stalls

---

## 📊 CURRENT STATUS

| Metric | Value |
|--------|-------|
| **Version** | v0.2.0 |
| **Build Status** | Debug: Running ✅ \| Release: Compiling ⏳ |
| **Test Coverage** | 17+ suites passing ✅ |
| **Source Lines** | ~19 core modules |
| **Security Modules** | 14 security components |
| **Unit Tests** | 100+ test functions |
| **GPU Compatibility** | Integrated GPUs (Intel Iris) ✅ |

---

## 🎮 CONTROLS REFERENCE

| Input | Action |
|-------|--------|
| `W A S D` | Move forward/back/strafe left/right |
| `Space` | Jump (tap=short hop, hold=full jump) / Ascend (creative/spectator) |
| `Shift` | Sneak / Descend |
| Mouse | Look around |
| `Left Click` | Break block |
| `Right Click` | Place block / Eat food / Drink water |
| `1-9`, `0`, `-` | Select hotbar slot |
| `E` | Open/close inventory |
| `T` | Open chat |
| `Tab` / `↑` / `↓` | Autocomplete / suggestion navigation |
| `Enter` | Send chat message / Start game |
| `F2` | Screenshot (saved to game directory) |
| `Esc` | Close inventory / Quit |

---

## 📝 CHANGE LOG

### v0.2.0 (2026-08-05)
- Added `FastNoiseLite.kt` for advanced noise generation
- Added `Player.kt` with extracted physics/collision
- Added `NativeModsScreen.kt` and `ModListScreen.kt`
- Added `Platform.kt` for platform detection
- Integrated Security Guardion with full IIV/ZKP system
- Achieved stable debug build on Intel Iris iGPU
- Release build compilation in progress
- Freebuff compiling cimgui for Monaco Editor integration

### v0.1.0 (Previous)
- Initial Vulkan engine setup
- Basic chunk streaming
- Simple terrain generation
- Initial HUD implementation

---

*Universal context file — readable by all AI agents*
*Last updated: 2026-08-05 23:17 CDT*
*Project: Strata Primordial | Studio: Novusforge Studios*
