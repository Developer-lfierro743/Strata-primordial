# 🎮 Strata Primordial

**Juego de sandbox voxel original** — Kotlin Multiplatform + SDL3 + Vulkan + ECS.

Construido desde cero. Sin código de Minecraft. Sin assets de Minecraft. 100% original.

---

## 📖 ¿Qué es Strata?

Strata Primordial es un juego de sandbox voxel de supervivencia inspirado por la visión original de Notch en 2009 para Minecraft — pero más ambicioso. Estamos construyendo lo que Minecraft *podría haber sido* con tecnología moderna:

- **Los planes originales de Notch**: Sky dimension, muebles, mejor IA, mob vote losers, aldeas autosostenibles
- **Nombres científicos reales de minerales** (ferroso, etc.) en lugar de nombres inventados
- **Crafteo por niveles**: Básico → Avanzado → Overpowered (progresión de desbloqueo)
- **THE PURGE**: Evento de experimento social de 100 jugadores inspirado por Sword4000
- **SafetyGuardian**: Suite de seguridad de nivel empresarial — no es IA, no es ML, reglas hardcodeadas para proteger a los jugadores

Esto **NO** es un mod de Minecraft. Esto es un **juego original independiente**.

---

## 🛠️ Stack de Tecnología

| Capa | Tecnología | Estado |
|------|------------|--------|
| Lenguaje | Kotlin 2.4.0 (KMP) | ✅ |
| Gráficos | Vulkan 1.4 (vulkan-kotlin) | ✅ |
| Ventanas | SDL3 | ✅ |
| Matemáticas | Strata3D (librería vec/mat custom) | ✅ |
| Renderizado | Strata3D engine (C Vulkan renderer) | 🔧 |
| Gen. Mundo | FastNoiseLite + terreno custom | ✅ |
| Meshing | ChunkMesher con face culling | ✅ |
| UI | cimgui (ImGui para Kotlin/Native) | ✅ |
| Seguridad | SafetyGuardian (30+ módulos) | ✅ |
| Modding | API de mods nativa en Kotlin | 🔧 |
| Shaders | SPIR-V voxel + agua | ✅ |

---

## 🖥️ Plataformas Objetivo

| Plataforma | Target | Estado |
|------------|--------|--------|
| Windows x64 | `desktop` (mingwX64) | 🔧 CI builds |
| Linux x64 | `linuxX64` | 🔧 CI builds |
| Android | Futuro | 📋 Planeado |
| iOS | Futuro | 📋 Planeado |
| macOS | Futuro | 📋 Planeado |

Ambos targets de escritorio se compilan vía **GitHub Actions CI** — no se requiere máquina x86_64 local.

---

## 🚀 Compilar

### Prerrequisitos
- JDK 17 (Amazon Corretto recomendado)
- Gradle 9.5.0
- Vulkan SDK 1.4.357+ (para desarrollo local)
- SDL3 source
- cimgui source

### Comandos de Compilación
```bash
# Windows (nativo, requiere MinGW)
./gradlew :linkReleaseExecutableDesktop

# Linux x64 (nativo o CI cross-compile)
./gradlew :linkDebugExecutableLinuxX64

# Empaquetar distribución
./gradlew packageDist
```

### CI/CD
GitHub Actions compila ambos targets en cada push a `master`:
- `ubuntu-latest` → linuxX64
- `windows-latest` → desktop (mingwX64)

---

## 📁 Estructura del Proyecto

```
strata/
├── src/
│   ├── commonMain/kotlin/strata/          # Código multiplataforma
│   │   ├── World.kt, ChunkMesher.kt       # Mundo voxel + meshing
│   │   ├── WorldStreamer.kt               # Streaming de chunks
│   │   ├── FastNoiseLite.kt               # Terreno procedural
│   │   ├── Frustum.kt                     # Frustum culling
│   │   ├── Player.kt, CameraMath.kt       # Jugador + cámara
│   │   ├── Inventory.kt, InventoryScreen.kt
│   │   ├── Hud.kt, Screen.kt, TitleScreen.kt
│   │   ├── Chat.kt, ChatCommands.kt, ChatOverlay.kt
│   │   ├── Terrain.kt, Survival.kt, GameMode.kt
│   │   ├── Platform.kt                    # Abstracción de plataforma
│   │   ├── Ui.kt, ui/StrataUI.kt          # Sistema de UI
│   │   ├── cimgui/StrataImGui.kt          # Bindings ImGui
│   │   ├── ModListScreen.kt, NativeModsScreen.kt, ModEditorScreen.kt
│   │   ├── modapi/ModApi.kt               # API de modding
│   │   └── security/                      # 🔒 SafetyGuardian
│   │       ├── SafetyGuardian.kt          # Orquestador central
│   │       ├── SecurityGateway.kt, DefaultSecurityGateway.kt
│   │       ├── SecurityResult.kt, SecurityContext.kt
│   │       ├── AntiInjection.kt           # Integridad de proceso
│   │       ├── Shredder.kt                # Borrado seguro de archivos
│   │       ├── DevConfig.kt, ReputationManager.kt, ChatSecurityBridge.kt
│   │       ├── iiv/                       # Identity Intent Verification
│   │       │   ├── IIVEngine.kt, IIVGate.kt, IIVToken.kt
│   │       │   ├── IIVQuestionnaire.kt, IIVDecision.kt
│   │       ├── crypto/                    # Criptografía PQC + clásica
│   │       │   ├── RKPManager.kt, PQCProvider.kt, CryptoProvider.kt
│   │       ├── policy/                    # Reglas de seguridad
│   │       │   ├── SecurityRule.kt, SexualRule.kt
│   │       │   ├── ChatGuardian.kt, PVPIntegrityRule.kt
│   │       │   ├── BuildIntegrityRule.kt, SocialTrustMonitor.kt
│   │       │   ├── AVCManager.kt, IIVRule.kt, ModdingRule.kt
│   │       ├── copyright/                 # Protección de assets
│   │       │   ├── CopyrightRegistry.kt, MinecraftSignatureDB.kt
│   │       │   ├── ModCategorizer.kt, ContentProtectionRule.kt
│   │       │   ├── ModScanner.kt, ModFolderScanner.kt
│   │       │   ├── JarAnalyzer.kt, HsvAnalyzer.kt
│   │       │   ├── AssetImageScanner.kt, ShellScanner.kt
│   │       │   └── NsfwWordDB.kt
│   │       ├── memory/                    # Memoria segura
│   │       │   ├── MemoryIsolator.kt, SecureMemoryArena.kt
│   │       └── protocol/                  # Red segura
│   │           └── SecureTunnel.kt
│   ├── desktopMain/kotlin/strata/         # Específico de Windows
│   │   ├── Main.kt                        # Punto de entrada
│   │   ├── Platform.kt                    # Implementación de plataforma
│   │   ├── cimgui/StrataImGui.kt          # ImGui de escritorio
│   │   ├── webview/                       # WebView (editor de mods)
│   │   │   ├── WebView.kt, WebViewState.kt
│   │   ├── modapi/ModLoaderDesktop.kt
│   │   └── security/crypto/CryptoProvider.kt, file/FileProvider.kt
│   ├── commonTest/kotlin/strata/          # Tests unitarios
│   │   ├── WorldTest.kt, ChunkMesherTest.kt, FrustumTest.kt
│   │   ├── PlayerCollisionTest.kt, GameModeTest.kt
│   │   ├── InventoryTest.kt, HudTest.kt
│   │   ├── FastNoiseLiteTest.kt, TerrainTest.kt
│   │   └── ... (15 archivos de test)
│   ├── test/kotlin/strata/security/test/  # Tests de seguridad
│   │   └── ModScannerTest.kt
│   ├── nativeInterop/cinterop/            # Interop con C
│   │   ├── sdl3.def                       # Bindings SDL3
│   │   ├── cimgui.def                     # Bindings cimgui
│   │   ├── webview.def                    # Bindings WebView
│   │   ├── strata_sdl3_wrapper.h          # Wrapper C de SDL3
│   │   ├── strata_imgui.h / _impl.cpp     # Bridge C++ de ImGui
│   │   ├── strata_webview.h / .cpp        # Bridge C++ de WebView
│   │   ├── libstrata_imgui.a, libvma.a    # Librerías estáticas precompiladas
│   │   ├── vma_impl.cpp / .o              # VMA memory allocator
│   │   ├── test_cimgui.cpp                # Test de cimgui
│   │   └── strata3d/                      # 🎨 Renderizador Strata3D
│   │       ├── strata3d.h                 # Header principal
│   │       ├── strata3d_core.h            # Vulkan instance/device
│   │       ├── strata3d_swapchain.h       # Swapchain/depth/framebuffer
│   │       ├── strata3d_pipeline.h        # Graphics pipelines
│   │       ├── strata3d_buffer.h          # VMA buffer allocation
│   │       ├── strata3d_renderer.h        # Frame rendering
│   │       ├── strata3d_camera.h          # Cámara en primera persona
│   │       ├── strata3d_input.h           # SDL3 input
│   │       └── strata3d_math.h            # Matemáticas Vec/Mat
│   └── shaders/                           # Shaders Vulkan
│       ├── voxel.vert, voxel.frag         # Renderizado voxel
│       ├── water.vert, water.frag         # Renderizado de agua
│       └── *.spv                          # Bytecode SPIR-V compilado
├── docs/
│   ├── SECURITY_ARCHITECTURE.md           # Documentación SafetyGuardian
│   └── JVM_TO_KMP_MAPPING.md              # Guía de migración de API
├── .github/workflows/build.yml            # CI/CD
├── build.gradle.kts                       # Configuración de build
├── settings.gradle.kts
└── README.md                              # Este archivo
```

---

## 🔒 SafetyGuardian — Seguridad de Nivel Empresarial

La seguridad no es una idea tardía. Es el kernel del juego.

### Qué hace:
- **IIV (Identity Intent Verification)**: Vinculación de identidad criptográfica — sin evasión de baneos
- **ChatGuardian**: Moderación de chat en tiempo real con mensajes firmados
- **ModScanner**: Verificación de mods — chequeo de firma, análisis de contenido, detección de plagio
- **CopyrightRegistry**: Huella digital de assets — bloquea texturas robadas
- **AntiInjection**: Verificación de integridad de proceso
- **Shredder**: Borrado seguro de archivos (crypto wipe)
- **PQCProvider**: Criptografía post-cuántica (basada en retículos)
- **SocialTrustMonitor**: Análisis de riesgo conductual
- **SecureTunnel**: Protocolo de red cifrado

### Por qué es diferente a Minecraft:
| Característica | Minecraft | Strata |
|----------------|-----------|--------|
| Identidad | Email + password | Par de llaves criptográficas (Ed25519) |
| Evasión de baneo | Trivial (cuenta nueva) | Imposible (vinculado a hardware) |
| Seguridad en chat | Chat reporting (1.19.1) | Firma IIV + filtrado ML |
| Seguridad de mods | Ninguna | Scanner + firma criptográfica |
| Anti-cheat | Client-side (débil) | Kernel de seguridad de software |
| GDPR | Añadido después | Integrado (identidad ZKP) |

---

## 🎯 Roadmap

### ✅ Fase 1: Fundación (Actual)
- [x] Estructura de proyecto Kotlin Multiplatform
- [x] Interop SDL3 + Vulkan + cimgui
- [x] Mundo voxel con sistema de chunks
- [x] Chunk mesher con face culling
- [x] Generación de terreno FastNoiseLite
- [x] Frustum culling (Gribb-Hartmann)
- [x] Sistema de jugador + cámara
- [x] Sistema de inventario
- [x] HUD + pantallas de UI
- [x] Sistema de chat + comandos
- [x] Suite de seguridad SafetyGuardian (30+ módulos)
- [x] Fundamentos de API de modding
- [x] Renderizador Vulkan Strata3D (headers C)
- [x] Shaders SPIR-V
- [x] CI/CD para 2 targets
- [x] Tests unitarios (15+ archivos de test)

### 🔧 Fase 2: Gameplay Central
- [ ] Implementación del renderizador Vulkan (compilar Strata3D)
- [ ] Generación de mundo infinita
- [ ] Colocación + rotura de bloques
- [ ] Física del jugador + colisión
- [ ] Ciclo día/noche
- [ ] IA de mobs
- [ ] Sistema de crafteo (Básico → Avanzado → Overpowered)

### 📋 Fase 3: Online
- [ ] Red para multijugador
- [ ] Software de servidor
- [ ] Infraestructura de servidor IIV
- [ ] Multijugador multiplataforma

### 📋 Fase 4: Escalar
- [ ] Target Android (base linuxArm64)
- [ ] Target iOS

### 📋 Fase 5: Pulido
- [ ] Sistema de eventos THE PURGE
- [ ] API de modding (integración Monaco Editor)
- [ ] Steam Workshop / marketplace
- [ ] Shaders, iluminación, post-procesamiento
- [ ] Motor de audio

---

## 💰 Modelo de Negocio

- **$16.99 USD** (moneda regional)
- Gratis durante alpha/indev (tiempo limitado)
- Sin microtransacciones
- Skins gratis vía marketplace (economía de creadores)

---

## 🙏 Agradecimientos

- **Notch** — visión original de Minecraft e inspiración
- **Sword4000** — inspiración para el evento THE PURGE
- **Technoirlab** — bindings vulkan-kotlin
- **SDL3**, **Vulkan**, **cimgui**, **VMA** — librerías open source

---

## 📄 Licencia

Copyright Novusforge Studios. Todos los derechos reservados.

Strata es software propietario. No se usa código de Minecraft. No se incluyen assets de Minecraft.

---

**Construido con ❤️ usando Kotlin + Vulkan + SDL3**
