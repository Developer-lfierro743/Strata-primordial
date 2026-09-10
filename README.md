# Strata Primordial

**A Kotlin Multiplatform voxel game with enterprise-grade security.**

## 🎮 What is Strata?

Strata Primordial is an open-source voxel sandbox game built with:
- **Kotlin/Native** for cross-platform performance
- **Vulkan** for modern GPU rendering
- **SDL3** for windowing/input
- **Enterprise Security Suite** for safe multiplayer

## 🚀 Quick Start

### Build Requirements
- JDK 21+
- Gradle 8+
- Vulkan SDK 1.4.357+
- SDL3 source (built to `SDL-main/build-vs/Release/`)
- cimgui source (for ImGui integration)

### Build & Run
```bash
# Build release executable
gradle :desktopMain:linkReleaseExecutableDesktop

# Run the game
.\build\bin\desktop\releaseExecutable\strata-prototype.exe

# Package distribution
gradle packageDist
```

## 📁 Project Structure

```
src/
├── commonMain/kotlin/strata/       # Cross-platform game logic
│   ├── security/                   # Enterprise security suite
│   ├── cimgui/                     # ImGui Kotlin bindings
│   ├── WorldStreamer.kt            # Chunk streaming system
│   ├── ChunkMesher.kt              # GPU mesh generation
│   ├── Hud.kt                      # In-game HUD
│   └── TitleScreen.kt              # Main menu
├── desktopMain/kotlin/strata/      # Desktop-specific code
│   └── Main.kt                     # Entry point
├── nativeInterop/cinterop/         # Native bindings
│   ├── sdl3.def                    # SDL3 definitions
│   ├── cimgui.def                  # ImGui definitions
│   ├── strata_imgui.h              # ImGui wrapper header
│   └── strata_imgui_impl.cpp       # ImGui C++ implementation
└── shaders/                        # Vulkan shader programs
    ├── voxel.vert
    ├── voxel.frag
    ├── water.vert
    └── water.frag
```

## 🔒 Security Features

Strata includes an enterprise-grade security suite:

| Module | Purpose |
|--------|---------|
| `AntiInjection` | Process integrity verification |
| `ChatGuardian` | Real-time chat moderation |
| `SocialTrustMonitor` | Behavioral risk analysis |
| `ModScanner` | Malicious mod detection |
| `CopyrightRegistry` | Asset protection |

## 🛠️ Modding API

Native mod support coming soon with:
- **Monaco Editor** integration for in-game scripting
- **Kotlin/Native** mod API for high-performance mods
- **cimgui** powered UI for mod management

## 📊 Technical Specs

- **Engine**: Custom Vulkan renderer
- **Language**: Kotlin 2.4.0
- **Target**: Windows x64 (native executable)
- **Renderer**: Vulkan 1.3 with custom shaders
- **Chunk System**: Streaming with LOD (4 levels)
- **Frustum Culling**: Gribb-Hartmann plane extraction

## 🎯 Roadmap

### Phase 1: Early Access (v0.5.0)
- [x] Singleplayer survival
- [x] Chunk streaming
- [x] Basic HUD
- [x] Security suite
- [ ] ImGui-native menus
- [ ] Modding API
- [ ] Basic multiplayer

### Phase 2: Growth (v0.8.0)
- [ ] Multiplayer servers
- [ ] Nether dimension
- [ ] Enchanting system
- [ ] Boss fights
- [ ] Steam Workshop integration

### Phase 3: Full Release (v1.0.0)
- [ ] Console ports
- [ ] Cross-platform play
- [ ] Mobile (KMP)
- [ ] Adventure mode

## 🤝 Contributing

We welcome contributions! Please read our [Contributing Guide](CONTRIBUTING.md) first.

## 📄 License

Copyright Novusforge Studios. All rights reserved.

---

Built with ❤️ using Kotlin + Vulkan