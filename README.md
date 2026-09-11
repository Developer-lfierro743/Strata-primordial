# 🎮 Strata Primordial

**Original voxel sandbox game** — Kotlin Multiplatform + SDL3 + Vulkan + ECS.

Built from scratch. No Minecraft code. No Minecraft assets. 100% original.

---

## 📖 What is Strata?

Strata Primordial is a voxel survival sandbox game inspired by Notch's original 2009 vision for Minecraft — but more ambitious. We're building what Minecraft *could* have been with modern tech:

- **Notch's original plans**: Sky dimension, furniture, better AI, mob vote losers, self-sustaining villages
- **Real scientific ore names** (ferrous, etc.) instead of made-up ones
- **Tiered crafting**: Basic → Advanced → Overpowered (unlock progression)
- **THE PURGE**: 100-player social experiment event inspired by Sword4000
- **SafetyGuardian**: Enterprise-grade security kernel — not AI, not ML, hardcoded rules to protect players

This is **NOT** a Minecraft mod. This is a **standalone original game**.

---

## 🛠️ Tech Stack

| Layer | Technology | Status |
|-------|------------|--------|
| Language | Kotlin 2.4.0 (KMP) | ✅ |
| Graphics | Vulkan 1.4 (vulkan-kotlin) | ✅ |
| Windowing | SDL3 | ✅ |
| Math | Strata3D (custom vec/mat library) | ✅ |
| Rendering | Strata3D engine (C Vulkan renderer) | 🔧 |
| World Gen | FastNoiseLite + custom terrain | ✅ |
| Meshing | ChunkMesher with face culling | ✅ |
| UI | cimgui (ImGui for Kotlin/Native) | ✅ |
| Security | SafetyGuardian (30+ modules) | ✅ |
| Modding | Native Kotlin mod API | 🔧 |
| Shaders | SPIR-V voxel + water shaders | ✅ |

---

## 🖥️ Target Platforms

| Platform | Target | CI |
|----------|--------|-----|
| Windows x64 | `desktop` (mingwX64) | Local only |
| Linux x64 | `linuxX64` | ✅ CI builds |
| Android | Future | 📋 Planned |
| iOS | Future | 📋 Planned |
| macOS | Future | 📋 Planned |

Linux x64 builds via **GitHub Actions CI** on every push to `master`.

---

## 🚀 Build

### Prerequisites
- JDK 17 (Amazon Corretto recommended)
- Gradle 9.5.0
- Vulkan SDK 1.4.357+ (for local dev)
- SDL3 source
- cimgui source

### Build Commands
```bash
# Windows (native, requires MinGW)
./gradlew :linkReleaseExecutableDesktop

# Linux x64 (native or CI cross-compile)
./gradlew :linkDebugExecutableLinuxX64

# Linux ARM64 (CI cross-compile)
./gradlew :linkDebugExecutableLinuxArm64

# Package distribution
./gradlew packageDist
```

### CI/CD
GitHub Actions builds `linuxX64` on every push to `master`:
- `ubuntu-latest` → linuxX64 (SDL3 built from source, X11 + Wayland enabled)
- Windows (`desktop`) builds locally on your machine

---

## 📁 Project Structure

```
strata/
├── src/
│   ├── commonMain/kotlin/strata/          # Cross-platform game code
│   │   ├── World.kt, ChunkMesher.kt       # Voxel world + meshing
│   │   ├── WorldStreamer.kt               # Chunk streaming
│   │   ├── FastNoiseLite.kt               # Procedural terrain
│   │   ├── Frustum.kt                     # Frustum culling
│   │   ├── Player.kt, CameraMath.kt       # Player + camera
│   │   ├── Inventory.kt, InventoryScreen.kt
│   │   ├── Hud.kt, Screen.kt, TitleScreen.kt
│   │   ├── Chat.kt, ChatCommands.kt, ChatOverlay.kt
│   │   ├── Terrain.kt, Survival.kt, GameMode.kt
│   │   ├── Platform.kt                    # Platform abstraction
│   │   ├── Ui.kt, ui/StrataUI.kt          # UI system
│   │   ├── cimgui/StrataImGui.kt          # ImGui bindings
│   │   ├── ModListScreen.kt, NativeModsScreen.kt, ModEditorScreen.kt
│   │   ├── modapi/ModApi.kt               # Modding API
│   │   └── security/                      # 🔒 SafetyGuardian
│   │       ├── SafetyGuardian.kt          # Core orchestrator
│   │       ├── SecurityGateway.kt, DefaultSecurityGateway.kt
│   │       ├── SecurityResult.kt, SecurityContext.kt
│   │       ├── AntiInjection.kt           # Process integrity
│   │       ├── Shredder.kt                # Secure file deletion
│   │       ├── DevConfig.kt, ReputationManager.kt, ChatSecurityBridge.kt
│   │       ├── iiv/                       # Identity Intent Verification
│   │       │   ├── IIVEngine.kt, IIVGate.kt, IIVToken.kt
│   │       │   ├── IIVQuestionnaire.kt, IIVDecision.kt
│   │       ├── crypto/                    # PQC + classical crypto
│   │       │   ├── RKPManager.kt, PQCProvider.kt, CryptoProvider.kt
│   │       ├── policy/                    # Security rules
│   │       │   ├── SecurityRule.kt, SexualRule.kt
│   │       │   ├── ChatGuardian.kt, PVPIntegrityRule.kt
│   │       │   ├── BuildIntegrityRule.kt, SocialTrustMonitor.kt
│   │       │   ├── AVCManager.kt, IIVRule.kt, ModdingRule.kt
│   │       ├── copyright/                 # Asset protection
│   │       │   ├── CopyrightRegistry.kt, MinecraftSignatureDB.kt
│   │       │   ├── ModCategorizer.kt, ContentProtectionRule.kt
│   │       │   ├── ModScanner.kt, ModFolderScanner.kt
│   │       │   ├── JarAnalyzer.kt, HsvAnalyzer.kt
│   │       │   ├── AssetImageScanner.kt, ShellScanner.kt
│   │       │   └── NsfwWordDB.kt
│   │       ├── memory/                    # Secure memory
│   │       │   ├── MemoryIsolator.kt, SecureMemoryArena.kt
│   │       └── protocol/                  # Secure networking
│   │           └── SecureTunnel.kt
│   ├── desktopMain/kotlin/strata/         # Windows-specific
│   │   ├── Main.kt                        # Entry point
│   │   ├── Platform.kt                    # Platform impl
│   │   ├── cimgui/StrataImGui.kt          # Desktop ImGui
│   │   ├── webview/                       # WebView (mod editor)
│   │   │   ├── WebView.kt, WebViewState.kt
│   │   ├── modapi/ModLoaderDesktop.kt
│   │   └── security/crypto/CryptoProvider.kt, file/FileProvider.kt
│   ├── commonTest/kotlin/strata/          # Unit tests
│   │   ├── WorldTest.kt, ChunkMesherTest.kt, FrustumTest.kt
│   │   ├── PlayerCollisionTest.kt, GameModeTest.kt
│   │   ├── InventoryTest.kt, HudTest.kt
│   │   ├── FastNoiseLiteTest.kt, TerrainTest.kt
│   │   └── ... (15 test files)
│   ├── test/kotlin/strata/security/test/  # Security tests
│   │   └── ModScannerTest.kt
│   ├── nativeInterop/cinterop/            # C interop
│   │   ├── sdl3.def                       # SDL3 bindings
│   │   ├── cimgui.def                     # cimgui bindings
│   │   ├── webview.def                    # WebView bindings
│   │   ├── strata_sdl3_wrapper.h          # SDL3 C wrapper
│   │   ├── strata_imgui.h / _impl.cpp     # ImGui C++ bridge
│   │   ├── strata_webview.h / .cpp        # WebView C++ bridge
│   │   ├── libstrata_imgui.a, libvma.a    # Prebuilt static libs
│   │   ├── vma_impl.cpp / .o              # VMA memory allocator
│   │   ├── test_cimgui.cpp                # cimgui test
│   │   └── strata3d/                      # 🎨 Strata3D renderer
│   │       ├── strata3d.h                 # Main header
│   │       ├── strata3d_core.h            # Vulkan instance/device
│   │       ├── strata3d_swapchain.h       # Swapchain/depth/framebuffer
│   │       ├── strata3d_pipeline.h        # Graphics pipelines
│   │       ├── strata3d_buffer.h          # VMA buffer allocation
│   │       ├── strata3d_renderer.h        # Frame rendering
│   │       ├── strata3d_camera.h          # First-person camera
│   │       ├── strata3d_input.h           # SDL3 input
│   │       └── strata3d_math.h            # Vec/Mat math
│   └── shaders/                           # Vulkan shaders
│       ├── voxel.vert, voxel.frag         # Voxel rendering
│       ├── water.vert, water.frag         # Water rendering
│       └── *.spv                          # Compiled SPIR-V bytecode
├── docs/
│   ├── SECURITY_ARCHITECTURE.md           # SafetyGuardian docs
│   └── JVM_TO_KMP_MAPPING.md              # API migration guide
├── .github/workflows/build.yml            # CI/CD
├── build.gradle.kts                       # Build config
├── settings.gradle.kts
└── README.md                              # This file
```

---

## 🔒 SafetyGuardian — Enterprise Security

Security is not an afterthought. It's the kernel of the game.

### What it does:
- **IIV (Identity Intent Verification)**: Cryptographic identity binding — no ban evasion
- **ChatGuardian**: Real-time chat moderation with signed messages
- **ModScanner**: Mod verification — signature check, content analysis, plagiarism detection
- **CopyrightRegistry**: Asset fingerprinting — blocks stolen textures
- **AntiInjection**: Process integrity verification
- **Shredder**: Secure file deletion (crypto wipe)
- **PQCProvider**: Post-quantum cryptography (lattice-based)
- **SocialTrustMonitor**: Behavioral risk analysis
- **SecureTunnel**: Encrypted network protocol

### Why it's different from Minecraft:
| Feature | Minecraft | Strata |
|---------|-----------|--------|
| Identity | Email + password | Cryptographic keypair (Ed25519) |
| Ban evasion | Trivial (new account) | Impossible (hardware-bound) |
| Chat safety | Chat reporting (1.19.1) | IIV signing + ML filtering |
| Mod security | None | Scanner + crypto signing |
| Anti-cheat | Client-side (weak) | Software security kernel |
| GDPR | Bolted on | Built-in (ZKP identity) |

---

## 🎯 Roadmap

### ✅ Phase 1: Foundation (Current)
- [x] Kotlin Multiplatform project structure
- [x] SDL3 + Vulkan + cimgui interop
- [x] Voxel world with chunk system
- [x] Chunk mesher with face culling
- [x] FastNoiseLite terrain generation
- [x] Frustum culling (Gribb-Hartmann)
- [x] Player + camera system
- [x] Inventory system
- [x] HUD + UI screens
- [x] Chat system + commands
- [x] SafetyGuardian security suite (30+ modules)
- [x] Modding API foundation
- [x] Strata3D Vulkan renderer (C headers)
- [x] SPIR-V shaders
- [x] CI/CD for 3 targets
- [x] Unit tests (15+ test files)

### 🔧 Phase 2: Core Gameplay
- [ ] Vulkan renderer implementation (compile Strata3D)
- [ ] Infinite world generation
- [ ] Block placing + breaking
- [ ] Player physics + collision
- [ ] Day/night cycle
- [ ] Mob AI
- [ ] Crafting system (Basic → Advanced → Overpowered)

### 📋 Phase 3: Online
- [ ] Multiplayer networking
- [ ] Server software
- [ ] IIV server infrastructure
- [ ] Cross-platform multiplayer

### 📋 Phase 4: Scale Up
- [ ] Android target (linuxArm64 base)
- [ ] iOS target

### 📋 Phase 5: Polish
- [ ] THE PURGE event system
- [ ] Modding API (Monaco Editor integration)
- [ ] Steam Workshop / marketplace
- [ ] Shaders, lighting, post-processing
- [ ] Audio engine

---

## 💰 Business Model

- **$16.99 USD** (regional currency)
- Free during alpha/indev (limited time)
- No microtransactions
- Free skins via marketplace (creator economy)

---

## 🙏 Acknowledgments

- **Notch** — original Minecraft vision and inspiration
- **Sword4000** — THE PURGE event inspiration
- **Technoirlab** — vulkan-kotlin bindings
- **SDL3**, **Vulkan**, **cimgui**, **VMA** — open source libraries

---

## 📄 License

Copyright Novusforge Studios. All rights reserved.

Strata is proprietary software. No Mojang code used. No Minecraft assets included.

---

**Built with ❤️ using Kotlin + Vulkan + SDL3**
