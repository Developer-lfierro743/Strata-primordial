package strata

import io.technoirlab.volk.VK_QUEUE_GRAPHICS_BIT
import io.technoirlab.vulkan.ApplicationInfo
import io.technoirlab.vulkan.Vulkan
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import strata.sdl3.SDL_CreateWindow
import strata.sdl3.SDL_Delay
import strata.sdl3.SDL_DestroyWindow
import strata.sdl3.SDL_GetNumLogicalCPUCores
import strata.sdl3.SDL_GetPlatform
import strata.sdl3.SDL_GetPowerInfo
import strata.sdl3.SDL_GetSystemRAM
import strata.sdl3.SDL_Init
import strata.sdl3.SDL_Quit
import strata.sdl3.SDL_WINDOW_RESIZABLE
import strata.sdl3.strata_create_allocator
import strata.sdl3.strata_create_device
import strata.sdl3.strata_create_pipeline
import strata.sdl3.strata_create_ui_pipeline
import strata.sdl3.strata_create_water_pipeline
import strata.sdl3.strata_create_swapchain
import strata.sdl3.strata_create_vertex_buffer
import strata.sdl3.strata_create_water_buffer
import strata.sdl3.strata_create_vulkan_surface
import strata.sdl3.strata_destroy_allocator
import strata.sdl3.strata_destroy_device
import strata.sdl3.strata_destroy_pipeline
import strata.sdl3.strata_destroy_swapchain
import strata.sdl3.strata_destroy_vulkan_surface
import strata.sdl3.strata_get_device_queue
import strata.sdl3.strata_get_mouse_x
import strata.sdl3.strata_get_mouse_y
import strata.sdl3.strata_presentation_support
import strata.sdl3.strata_poll_input
import strata.sdl3.strata_render_frame
import strata.sdl3.strata_get_ticks
import strata.sdl3.strata_request_capture
import strata.sdl3.strata_capture_done
import strata.sdl3.strata_capture_ack
import strata.sdl3.strata_set_relative_mouse
import strata.sdl3.strata_key_down
import strata.sdl3.strata_set_dpi_aware
import strata.sdl3.strata_set_text_input
import strata.sdl3.strata_get_window_size_pixels
import strata.sdl3.strata_swapchain_needs_rebuild
import strata.webview.WebViewState

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.floor
import kotlin.math.roundToInt

private const val SDL_INIT_VIDEO = 0x00000020u
private const val SDL_WINDOW_VULKAN = 0x0000000010000000uL

/* ── SDL3 event types (from SDL_events.h) ──
 * IMPORTANT: these are the SDL3 values. SDL_EVENT_KEY_DOWN is 0x300 and
 * SDL_EVENT_MOUSE_BUTTON_DOWN is 0x401 (NOT the SDL2-style 0x401/0x403). */
private const val SDL_EVENT_QUIT = 0x100
private const val SDL_EVENT_WINDOW_RESIZED = 0x206
private const val SDL_EVENT_KEY_DOWN = 0x300
private const val SDL_EVENT_KEY_UP = 0x301
private const val SDL_EVENT_TEXT_INPUT = 0x303
private const val SDL_EVENT_MOUSE_MOTION = 0x400
private const val SDL_EVENT_MOUSE_BUTTON_DOWN = 0x401
private const val SDL_EVENT_MOUSE_BUTTON_UP = 0x402

private const val SDL_SCANCODE_W = 26
private const val SDL_SCANCODE_S = 22
private const val SDL_SCANCODE_A = 4
private const val SDL_SCANCODE_D = 7
private const val SDL_SCANCODE_T = 23   // opens chat
private const val SDL_SCANCODE_SPACE = 44
private const val SDL_SCANCODE_RETURN = 40
private const val SDL_SCANCODE_LSHIFT = 225
private const val SDL_SCANCODE_ESCAPE = 41
private const val SDL_SCANCODE_BACKSPACE = 42
private const val SDL_SCANCODE_TAB = 43
private const val SDL_SCANCODE_UP = 82
private const val SDL_SCANCODE_DOWN = 81
private const val SDL_SCANCODE_1 = 30
private const val SDL_SCANCODE_9 = 38   // 1..9 are contiguous (30..38)
private const val SDL_SCANCODE_0 = 39   // selects hotbar slot 10
private const val SDL_SCANCODE_MINUS = 45  // selects hotbar slot 11 (SDL3 value)
private const val SDL_SCANCODE_E = 8    // opens/closes the inventory
private const val SDL_SCANCODE_F2 = 59   // takes a screenshot (SDL3 value)
private const val SDL_MOUSE_BUTTON_LEFT = 1u
private const val SDL_MOUSE_BUTTON_RIGHT = 3u

/** Format a packed VK_MAKE_API_VERSION value as "major.minor.patch". */
private fun formatVulkanVersion(v: UInt): String =
    "${(v shr 22) and 0x7Fu}.${(v shr 12) and 0x3FFu}.${v and 0xFFFu}"

/* ── Application ──
 * Camera + matrix math (Camera, Mat4, perspectiveMatrix, lookAtMatrix,
 * computeMVP) lives in commonMain/CameraMath.kt; the world-buffer builder +
 * material split (WorldMeshSplit) lives in commonMain/WorldMeshSplit.kt. */

@OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)
fun main() {
    // DPI-aware FIRST: without this, Windows DPI-scales the window (logical
    // 1280x720 becomes a 1024x576 physical surface at 125%), which desyncs the
    // Vulkan swapchain size from the HUD coordinate space and pushes HUD
    // elements (hotbar, minimap) off-screen.
    strata_set_dpi_aware()
    check(SDL_Init(SDL_INIT_VIDEO)) { "SDL3 video initialization failed" }

    val window = SDL_CreateWindow("Strata: Primordial", 1280, 720, SDL_WINDOW_RESIZABLE or SDL_WINDOW_VULKAN)
        ?: error("SDL3 window creation failed")

    // The title screen needs a visible, free cursor; relative (locked) mouse
    // is only enabled once the player enters the game.
    if (!strata_set_relative_mouse(window, false)) {
        println("Warning: could not release relative mouse mode")
    }

    println("Strata: Primordial SDL3 window created")

    val vulkan = Vulkan()
    memScoped {
        val instance = vulkan.createInstance(
            enabledExtensions = listOf("VK_KHR_surface", "VK_KHR_win32_surface"),
            applicationInfo = ApplicationInfo(
                apiVersion = 0u,
                applicationName = "Strata: Primordial",
                engineName = "Strata Engine"
            )
        )
        println("Vulkan instance created: ${instance.handle}")

        val surface = strata_create_vulkan_surface(window, instance.handle)
            ?: error("SDL3 Vulkan surface creation failed")
        println("SDL3 Vulkan surface created: $surface")

        val devices = instance.enumeratePhysicalDevices()
        println("Physical devices: ${devices.size}")
        for (d in devices) {
            val props = d.getProperties()
            println("  ${props.deviceName?.toKString()}")
        }

        val selected = devices.firstOrNull()
            ?: error("No Vulkan physical device found")

        /* GPU identity for the HUD info panel (F3-style): deviceName is a C
         * string, apiVersion is packed VK_MAKE_API_VERSION. Captured once at
         * startup and copied to plain Kotlin Strings for the per-frame HUD. */
        val gpuProps = selected.getProperties()
        val gpuName = gpuProps.deviceName?.toKString() ?: "Unknown GPU"
        val gpuVulkanVersion = formatVulkanVersion(gpuProps.apiVersion)
        println("Selected: $gpuName (Vulkan $gpuVulkanVersion)")

        /* System info — SDL3 native (no extra deps; DeviceKit/KDeviceInfo are
         * JVM/Android/iOS-only and can't link against this Kotlin/Native
         * target). CPU cores, RAM, OS and power state feed the HUD info
         * panel next to the GPU line. */
        val cpuCores = SDL_GetNumLogicalCPUCores()
        // Round to the nearest GB (SDL reports MB; a 16 GB machine reports ~16255).
        val ramGb = (SDL_GetSystemRAM() + 512) / 1024
        val platform = SDL_GetPlatform()?.toKString() ?: "Unknown"
        val powerText = memScoped {
            // SDL allows NULL for the seconds out-param — we only need the %.
            val pct = alloc<IntVar>()
            val st = SDL_GetPowerInfo(null, pct.ptr)
            // SDL3 SDL_PowerState: 1=ON_BATTERY 2=NO_BATTERY 3=CHARGING 4=CHARGED
            // (bound as Int because the enum includes -1 = ERROR)
            val label = when (st) {
                3 -> "CHARGING"; 4 -> "CHARGED"
                1 -> "ON BATTERY"; 2 -> "PLUGGED IN"
                else -> "UNKNOWN"
            }
            if (pct.value >= 0) "$label ${pct.value}%" else label
        }
        val cpuLine = "$cpuCores cores · $ramGb GB RAM"
        val osLine = "$platform · $powerText"
        println("System: $cpuLine | $osLine")

        val queueFamilies = selected.getQueueFamilyProperties()
        val queueIndex = queueFamilies.indexOfFirst { props ->
            props.queueFlags and VK_QUEUE_GRAPHICS_BIT != 0u
        }
        check(queueIndex >= 0) { "No graphics queue family found" }

        val presentOk = strata_presentation_support(instance.handle, selected.handle, queueIndex)
        println("Queue family $queueIndex: graphics + present = $presentOk")

        val device = strata_create_device(instance.handle, selected.handle, queueIndex)
            ?: error("Failed to create logical device")
        println("Logical device created: $device")

        val queue = strata_get_device_queue(instance.handle, device, queueIndex)
        println("Queue obtained: $queue")

        /* ── VMA Allocator (replaces manual vkAllocateMemory calls) ── */
        check(strata_create_allocator(instance.handle, selected.handle, device)) { "Failed to create VMA allocator" }
        println("VMA allocator created")

        /* ── Initial window size: use PHYSICAL PIXELS (SDL_GetWindowSizeInPixels).
         * The Vulkan swapchain and viewport are in pixels; under DPI scaling the
         * logical size (SDL_GetWindowSize) is smaller (e.g. 1024x576 vs 1280x720
         * at 125%), so the HUD must be laid out in the same pixel space as the
         * swapchain or its bottom elements get clipped off-screen. ── */
        var windowWidth = 1280
        var windowHeight = 720
        memScoped {
            val w = alloc<IntVar>()
            val h = alloc<IntVar>()
            strata_get_window_size_pixels(window, w.ptr, h.ptr)
            if (w.value > 0 && h.value > 0) {
                windowWidth = w.value
                windowHeight = h.value
            }
        }
        println("Window size (pixels): ${windowWidth}x${windowHeight}")

        var swapchain = strata_create_swapchain(instance.handle, selected.handle, device, surface, queueIndex.toUInt(), windowWidth, windowHeight)
        checkNotNull(swapchain) { "Failed to create Vulkan swapchain" }
        println("Vulkan swapchain created successfully")

        /* ── Create graphics pipeline (with MVP push constants) ── */
        var pipeline = strata_create_pipeline(instance.handle, selected.handle, device, swapchain)
        checkNotNull(pipeline) { "Failed to create Vulkan graphics pipeline" }

        /* ── Transparent water pipeline: same vertex format, but alpha-blended
         * with the water shader pair (fresnel + animated surface). ── */
        var waterPipeline = strata_create_water_pipeline(instance.handle, selected.handle, device, swapchain)
            ?: error("Failed to create water pipeline")
        println("Water pipeline allocated (blend on)")

        /* ── 2D UI overlay pipeline: same shaders, but NO depth test/write, ──
         * so overlay quads (title screen, HUD) always draw on top of the world. */
        var uiPipeline = strata_create_ui_pipeline(instance.handle, selected.handle, device, swapchain)
            ?: error("Failed to create UI overlay pipeline")
        println("UI overlay pipeline allocated (depth off)")

        /* ── Game state (created lazily when the player clicks Singleplayer) ── */
        val chunkSize = 32
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var world: World? = null
        var streamer: WorldStreamer? = null
        var player: Player? = null
        var inventory: PlayerInventory? = null
        var camera = Camera(x = 0f, y = 13f, z = 0f, yaw = 0f, pitch = 0f)

        var currentData = FloatArray(0)
        var currentVertexCount = 0
        var currentWaterData = FloatArray(0)
        var currentWaterVertexCount = 0
        /** Per-chunk vertex ranges in [currentData]/[currentWaterData]; the
         * per-frame frustum culling filters this list before each draw. */
        var currentRanges: List<WorldMeshSplit.ChunkRange> = emptyList()
        var pendingMesh: Deferred<WorldMeshSplit.Split>? = null
        var pendingEpoch = -1
        var lastMeshRebuildTime = 0u // timestamp of last mesh rebuild request
        val MESH_REBUILD_COOLDOWN_MS = 100u // minimum ms between rebuilds

        fun uploadVertexBuffer() {
            // Empty world buffer (title screen, or world not built yet): nothing
            // to upload — addressOf(0) on an empty array would crash on resize.
            if (currentVertexCount == 0) return
            currentData.usePinned { pinned ->
                strata_create_vertex_buffer(instance.handle, selected.handle, device, pipeline, pinned.addressOf(0), currentVertexCount.toUInt())
            }
        }

        fun uploadWaterBuffer() {
            if (currentWaterVertexCount == 0) return
            currentWaterData.usePinned { pinned ->
                strata_create_water_buffer(instance.handle, selected.handle, device, waterPipeline, pinned.addressOf(0), currentWaterVertexCount.toUInt())
            }
        }

        /**
         * Kick off the whole-world mesh concat on a background thread. The
         * FloatArrays in each ChunkMesh are immutable snapshots, so this is
         * safe to do off the render thread — the render loop never stalls on it.
         * The build also splits the combined buffer into opaque + water.
         */
        fun requestMeshRebuild() {
            val s = streamer ?: return
            if (pendingMesh != null) return
            // Cooldown: don't rebuild more often than every MESH_REBUILD_COOLDOWN_MS
            val now = strata_get_ticks()
            if (now - lastMeshRebuildTime < MESH_REBUILD_COOLDOWN_MS) return
            lastMeshRebuildTime = now
            pendingEpoch = s.meshEpoch()
            
            // Use incremental update if we have existing ranges and only some chunks changed
            val dirtyKeys = s.getDirtyChunks()
            if (currentRanges.isNotEmpty() && dirtyKeys.isNotEmpty() && dirtyKeys.size < currentRanges.size / 2) {
                // Incremental update: only rebuild changed chunks
                val newMeshes = dirtyKeys.mapNotNull { key ->
                    s.meshFor(key)?.let { key to it }
                }.toMap()
                pendingMesh = scope.async(Dispatchers.Default) {
                    val currentSplit = WorldMeshSplit.Split(currentData, currentWaterData, currentRanges)
                    val result = WorldMeshSplit.incrementalUpdate(currentSplit, dirtyKeys, newMeshes, chunkSize)
                    WorldMeshSplit.Split(result.solid, result.water, result.ranges)
                }
            } else {
                // Full rebuild: either first time, or too many chunks changed
                val entries = s.meshKeys().map { it to s.meshFor(it)!! }
                pendingMesh = scope.async(Dispatchers.Default) {
                    WorldMeshSplit.split(entries, chunkSize)
                }
            }
        }

        /** Synchronous one-shot build used to fill the first frame of the game. */
        fun rebuildMeshNow() {
            val s = streamer ?: return
            val split = WorldMeshSplit.split(s.meshKeys().map { it to s.meshFor(it)!! }, chunkSize)
            currentData = split.solid
            currentVertexCount = currentData.size / 7
            currentWaterData = split.water
            currentWaterVertexCount = currentWaterData.size / 7
            currentRanges = split.ranges
            uploadVertexBuffer()
            uploadWaterBuffer()
            println("World mesh rebuilt: ${currentVertexCount} solid + ${currentWaterVertexCount} water verts across ${s.meshKeys().size} chunks (${currentRanges.size} ranges)")
        }

        /* ── Survival state (drives the animated HUD bars) ──
         * Truth lives in [Survival] (double hearts, hunger, thirst, XP); the
         * display/ghost values below are eased toward it per frame so the
         * bars animate smoothly (Minecraft damage-trail style). */
        var selectedSlot = 0
        val survival = Survival()
        var healthDisplay = survival.health
        var hungerDisplay = survival.hunger
        var thirstDisplay = survival.thirst
        var healthGhost = survival.health
        var hungerGhost = survival.hunger
        var thirstGhost = survival.thirst

        /* ── Game mode (survival / creative / spectator) ──
         * Switched with /gamemode in chat. Drives the player physics (walk vs
         * fly vs no-clip), the survival sim (only in survival) and which HUD
         * elements draw. */
        var gameMode = GameMode.SURVIVAL

        /* ── Chat system (T to open) ──
         * Text arrives as SDL_EVENT_TEXT_INPUT events (the OS delivers real
         * characters, shift-aware); commands run through [ChatCommands] with
         * live autocomplete suggestions rendered by [ChatOverlay]. */
        val chat = ChatLog()
        var chatOpen = false
        var chatSuggestions: List<String> = emptyList()
        var chatSuggestionIndex = 0

        fun refreshChatSuggestions() {
            chatSuggestions = ChatCommands.complete(chat.input)
            if (chatSuggestionIndex >= chatSuggestions.size) chatSuggestionIndex = 0
        }

        fun openChat() {
            if (chatOpen || ScreenManager.current != Screen.GAME) return
            chatOpen = true
            chat.historyIndex = -1
            chatSuggestionIndex = 0
            refreshChatSuggestions()
            strata_set_text_input(window, true)
        }

        fun closeChat() {
            if (!chatOpen) return
            chatOpen = false
            strata_set_text_input(window, false)
        }

        /** Send the current input: commands execute, everything else is chat. */
        fun submitChat() {
            val inputText = chat.input.trim()
            chat.commitInput() // Routes through security bridge internally
            closeChat()
            if (inputText.isBlank()) return
            if (inputText.startsWith("/")) {
                val result = ChatCommands.execute(
                    inputText,
                    ChatCommands.Context(
                        seed = world?.seed ?: 12345,
                        setGameMode = { gameMode = it },
                        playerId = chat.playerId
                    )
                )
                when (result) {
                    null -> {} // Already handled by commitInput
                    is ChatCommands.Result.Message -> chat.addLine(result.text, system = true)
                    ChatCommands.Result.ClearChat -> chat.clear()
                    ChatCommands.Result.Unknown ->
                        chat.addLine("Unknown command — type /help for commands.", system = true)
                }
            }
            // Normal messages are already added by commitInput() through the security bridge
        }

        /* ── Mod registry state ── */
        val modRegistry = object {
            val names = mutableListOf("ExampleMod", "TerrainPlus", "SoundPack", "BetterLighting")
            val enabled = mutableListOf(true, false, true, false)
        }
        var modListScroll = 0
        
        /* ── WebView state for Mod Editor ── */
        val webViewState = WebViewState()
        var webViewInitialized = false

        /* ── Track keyboard state for smooth movement ── */
        val keyState = BooleanArray(512)

        /**
         * Build the world, pre-warm it so the first frame is fully solid
         * (no gaps), and switch to the GAME screen. Runs once on the
         * Singleplayer button.
         */
        fun enterGame() {
            if (ScreenManager.current == Screen.GAME) return
            println("Starting singleplayer world (seed 12345)...")
            val w = World(seed = 12345, size = chunkSize)
            val st = WorldStreamer(
                w,
                renderDistance = 3,
                verticalRenderDistance = 2,
                scope = scope,
                genWorkerCount = (cpuCores / 2).coerceIn(2, 8),
                meshWorkerCount = cpuCores.coerceIn(2, 12)
            )
            println("WorldStreamer: ${st.genWorkerCount} gen workers, ${st.meshWorkerCount} mesh workers")
            world = w
            streamer = st

            val prewarmStart = strata_get_ticks()
            // Compute where the player will land BEFORE pre-warming.
            val groundY = Terrain.heightAt(w.seed, 0, 0).coerceAtLeast(Terrain.SEA_LEVEL) + 1f
            val spawnCy = w.worldToChunkY(groundY.toInt())
            // Pre-warm 2 horizontal layers: the player's chunk and one below
            // (for collision when falling). Only wait for BLOCK DATA (not mesh)
            // so the player doesn't fall through unloaded chunks.
            var prewarmTicks = 0
            while (prewarmTicks < 3000) {
                st.update(w.worldToChunkX(0), spawnCy, w.worldToChunkZ(0))
                // Also trigger generation of the layer below for safety
                st.update(w.worldToChunkX(0), spawnCy - 1, w.worldToChunkZ(0))
                var blocksReady = true
                for (dx in -st.renderDistance..st.renderDistance) {
                    for (dz in -st.renderDistance..st.renderDistance) {
                        val key0 = w.chunkKey(w.worldToChunkX(0) + dx, spawnCy, w.worldToChunkZ(0) + dz)
                        val key1 = w.chunkKey(w.worldToChunkX(0) + dx, spawnCy - 1, w.worldToChunkZ(0) + dz)
                        if (!w.hasChunk(key0) || !w.hasChunk(key1)) {
                            blocksReady = false
                            break
                        }
                    }
                    if (!blocksReady) break
                }
                if (blocksReady) break
                SDL_Delay(20u)
                prewarmTicks++
            }
            println("Pre-warmed ${w.loadedKeys.size} chunks (${st.meshKeys().size} meshed) in ${(strata_get_ticks() - prewarmStart).toFloat() / 1000f}s")

            if (st.meshKeys().isNotEmpty()) {
                rebuildMeshNow()
                st.markMeshUploaded()
            }

            /* Place the player's FEET on the ground: heightAt + 1 sits on top
             * of the surface block (or just above sea level if in a lake).
             * The camera then rides at eye height (+1.62). */
            player = Player(w).also { it.position = Vec3(0f, groundY, 0f) }
            camera = Camera(x = 0f, y = groundY + Player.EYE_HEIGHT, z = 0f, yaw = 0f, pitch = 0f)
            inventory = PlayerInventory()

            if (!strata_set_relative_mouse(window, true)) {
                println("Warning: relative mouse mode failed (look may be limited)")
            }
            ScreenManager.switchTo(Screen.GAME)
            println("Controls: WASD=move, Space=up, Shift=down, Mouse=look, LeftClick=break, RightClick=place, 1-9/0/-=slot, E=inventory, ESC=quit")
        }

        /* ── Inventory open/close: E toggles it, the cursor is freed while
         * the inventory is open so slots can be clicked. ── */
        fun openInventory() {
            if (ScreenManager.current != Screen.GAME) return
            ScreenManager.switchTo(Screen.INVENTORY)
            strata_set_relative_mouse(window, false)
        }
        fun closeInventory() {
            if (ScreenManager.current != Screen.INVENTORY) return
            ScreenManager.switchTo(Screen.GAME)
            strata_set_relative_mouse(window, true)
        }

        /* ── Helper: rebuild swapchain + pipeline after resize ── */
        fun rebuildSwapchain(newWidth: Int, newHeight: Int): Boolean {
            if (newWidth <= 0 || newHeight <= 0) return false
            println("Rebuilding swapchain: ${newWidth}x${newHeight}")

            strata_destroy_pipeline(instance.handle, device, pipeline)
            strata_destroy_pipeline(instance.handle, device, waterPipeline)
            strata_destroy_pipeline(instance.handle, device, uiPipeline)
            strata_destroy_swapchain(instance.handle, device, swapchain)

            windowWidth = newWidth
            windowHeight = newHeight

            swapchain = strata_create_swapchain(instance.handle, selected.handle, device, surface, queueIndex.toUInt(), windowWidth, windowHeight)
            if (swapchain == null) {
                println("WARNING: Failed to recreate Vulkan swapchain — device may be lost")
                return false
            }
            pipeline = strata_create_pipeline(instance.handle, selected.handle, device, swapchain)
                ?: run { println("WARNING: Failed to recreate graphics pipeline"); return false }
            waterPipeline = strata_create_water_pipeline(instance.handle, selected.handle, device, swapchain)
                ?: run { println("WARNING: Failed to recreate water pipeline"); return false }
            uiPipeline = strata_create_ui_pipeline(instance.handle, selected.handle, device, swapchain)
                ?: run { println("WARNING: Failed to recreate UI pipeline"); return false }

            uploadVertexBuffer()
            uploadWaterBuffer()
            println("Swapchain rebuilt: ${windowWidth}x${windowHeight}")
            return true
        }

        /* ── Fixed timestep (formula #26: "fixing lag problems with fixed
         * timestep"). Game simulation steps at a constant 60 Hz regardless of
         * render frame rate, so movement and survival never speed up or jitter
         * when a frame hiccups — the accumulator absorbs the variance instead.
         * Per-frame work (streaming, mesh uploads, HUD) still runs every frame.
         * ── */
        val FIXED_DT = 1f / 60f
        val MAX_FRAME_TIME = 0.25f   // clamp huge hitches so we don't spiral

        var running = true
        var lastTime = strata_get_ticks()
        var accumulator = 0f
        var fps = 60
        var frameCount = 0
        var fpsLastTick = strata_get_ticks()
        /* Name of the screenshot currently being captured inline by render_frame. */
        var pendingShotName = ""
        var strataImGuiInitialized = false
        println("Main loop entered")
        while (running) {
            val currentTime = strata_get_ticks()
            var frameTime = (currentTime - lastTime).toFloat() / 1000f
            lastTime = currentTime
            if (frameTime > MAX_FRAME_TIME) frameTime = MAX_FRAME_TIME
            accumulator += frameTime
            // Don't bank time while not in the game or typing in chat —
            // otherwise opening the inventory/chat would unleash a catch-up
            // burst of movement ticks.
            if (ScreenManager.current != Screen.GAME || chatOpen) accumulator = 0f

            /* Track FPS (updated twice a second for the HUD) */
            frameCount++
            if (currentTime - fpsLastTick >= 500u) {
                fps = (frameCount * 1000) / (currentTime - fpsLastTick).toInt()
                frameCount = 0
                fpsLastTick = currentTime
            }

            /* ── Poll input events (pass window for resize queries) ── */
            memScoped {
                val inputEvent = alloc<strata.sdl3.StrataInputEvent>()
                while (strata_poll_input(inputEvent.ptr, window)) {
                    // Forward SDL events to ImGui when it's active
                    if (strataImGuiInitialized && ScreenManager.current == Screen.NATIVE_MODS) {
                        strata_cimgui.StrataImGui.processEvent(inputEvent.ptr)
                    }
                    
                    when (inputEvent.eventType) {
                        SDL_EVENT_QUIT -> running = false

                        SDL_EVENT_WINDOW_RESIZED -> {
                            // New dimensions come from the event struct (set by C helper).
                            // The C helper reports LOGICAL size; convert to pixels so the
                            // swapchain and HUD stay in the same coordinate space.
                            val newW = inputEvent.windowWidth
                            val newH = inputEvent.windowHeight
                            if (newW > 0 && newH > 0) {
                                memScoped {
                                    val pw = alloc<IntVar>()
                                    val ph = alloc<IntVar>()
                                    strata_get_window_size_pixels(window, pw.ptr, ph.ptr)
                                    rebuildSwapchain(if (pw.value > 0) pw.value else newW, if (ph.value > 0) ph.value else newH)
                                }
                            }
                        }

                        SDL_EVENT_KEY_DOWN -> {
                            val sc = inputEvent.keySymbol
                            // F2 screenshot works everywhere, even while typing.
                            if (sc == SDL_SCANCODE_F2) {
                                // Request an INLINE screenshot: render_frame reads
                                // back the CURRENT app-owned image (a presented
                                // image belongs to the display engine — reading it
                                // back is a spec violation that hangs Intel's GPU),
                                // writes the BMP and sets captureResult, which we
                                // check right after this frame renders.
                                val shotName = "screenshot_${strata_get_ticks()}.bmp"
                                strata_request_capture(swapchain, shotName)
                                pendingShotName = shotName
                            } else if (chatOpen) {
                                // While typing, every key goes to the chat box.
                                when (sc) {
                                    SDL_SCANCODE_ESCAPE -> closeChat()
                                    SDL_SCANCODE_RETURN -> submitChat()
                                    SDL_SCANCODE_BACKSPACE -> {
                                        chat.input = chat.input.dropLast(1)
                                        refreshChatSuggestions()
                                    }
                                    SDL_SCANCODE_TAB -> {
                                        // Accept the highlighted suggestion (Minecraft Tab).
                                        if (chatSuggestions.isNotEmpty()) {
                                            val pick = chatSuggestions[chatSuggestionIndex.coerceIn(0, chatSuggestions.size - 1)]
                                            chat.input = ChatCommands.applySuggestion(chat.input, pick) + " "
                                            refreshChatSuggestions()
                                        }
                                    }
                                    SDL_SCANCODE_UP -> {
                                        if (chatSuggestions.isNotEmpty()) {
                                            chatSuggestionIndex = (chatSuggestionIndex - 1 + chatSuggestions.size) % chatSuggestions.size
                                        } else {
                                            chat.recallUp()?.let {
                                                chat.input = it
                                                refreshChatSuggestions()
                                            }
                                        }
                                    }
                                    SDL_SCANCODE_DOWN -> {
                                        if (chatSuggestions.isNotEmpty()) {
                                            chatSuggestionIndex = (chatSuggestionIndex + 1) % chatSuggestions.size
                                        } else {
                                            chat.input = chat.recallDown() ?: ""
                                            refreshChatSuggestions()
                                        }
                                    }
                                }
                            } else {
                                if (sc == SDL_SCANCODE_ESCAPE) {
                                    // ESC: close sub-screens first, then inventory, then quit.
                                    when (ScreenManager.current) {
                                        Screen.MOD_LIST, Screen.MOD_EDITOR -> {
                                            println("Back to Native Mods hub")
                                            ScreenManager.switchTo(Screen.NATIVE_MODS)
                                        }
                                        Screen.NATIVE_MODS -> {
                                            println("Back to title screen")
                                            ScreenManager.goToTitle()
                                        }
                                        Screen.INVENTORY -> closeInventory()
                                        else -> running = false
                                    }
                                }
                                if (sc == SDL_SCANCODE_E) {
                                    // E toggles the inventory (in-game only).
                                    when (ScreenManager.current) {
                                        Screen.GAME -> openInventory()
                                        Screen.INVENTORY -> closeInventory()
                                        else -> {}
                                    }
                                }
                                if (sc == SDL_SCANCODE_T && ScreenManager.current == Screen.GAME) {
                                    openChat()
                                }
                                if (ScreenManager.current == Screen.TITLE) {
                                    // Enter / Space start a singleplayer world.
                                    if (sc == SDL_SCANCODE_RETURN || sc == SDL_SCANCODE_SPACE) enterGame()
                                } else if (ScreenManager.current == Screen.MOD_LIST) {
                                    // Up/Down arrows scroll the mod list.
                                    when (sc) {
                                        SDL_SCANCODE_UP -> modListScroll = (modListScroll - 1).coerceAtLeast(0)
                                        SDL_SCANCODE_DOWN -> modListScroll = (modListScroll + 1)
                                            .coerceAtMost(maxOf(0, modRegistry.names.size - 12))
                                    }
                                } else {
                                    // Hotbar hotkeys: 1-9, 0 and - select slots 0..10.
                                    when {
                                        sc in SDL_SCANCODE_1..SDL_SCANCODE_9 -> selectedSlot = sc - SDL_SCANCODE_1
                                        sc == SDL_SCANCODE_0 -> selectedSlot = 9
                                        sc == SDL_SCANCODE_MINUS -> selectedSlot = 10
                                    }
                                }
                            }
                            if (sc in keyState.indices) keyState[sc] = true
                        }

                        SDL_EVENT_KEY_UP -> {
                            val sc = inputEvent.keySymbol
                            if (sc in keyState.indices) keyState[sc] = false
                        }

                        SDL_EVENT_TEXT_INPUT -> {
                            // The OS delivers real typed characters (shift-aware,
                            // punctuation) straight into the chat box.
                            if (chatOpen) {
                                val text = inputEvent.text?.toKString() ?: ""
                                if (text.isNotEmpty()) {
                                    chat.input += text
                                    refreshChatSuggestions()
                                }
                            }
                        }

                        SDL_EVENT_MOUSE_MOTION -> {
                            // Camera look only applies in-game (cursor is locked there),
                            // and not while the chat box is capturing the keyboard.
                            if (ScreenManager.current == Screen.GAME && !chatOpen) {
                                val sensitivity = 0.003f
                                camera = camera.copy(
                                    yaw = camera.yaw - inputEvent.mouseDx * sensitivity,
                                    pitch = (camera.pitch + inputEvent.mouseDy * sensitivity).coerceIn(-1.5f, 1.5f)
                                )
                            }
                        }

                        SDL_EVENT_MOUSE_BUTTON_DOWN -> {
                            when (inputEvent.mouseButton) {
                                SDL_MOUSE_BUTTON_LEFT.toInt() -> {
                                    when (ScreenManager.current) {
                                        Screen.TITLE -> {
                                            // Title menu: hit-test the buttons.
                                            val mx = strata_get_mouse_x(window)
                                            val my = strata_get_mouse_y(window)
                                            when (TitleScreen.buttonAt(mx, my, windowWidth, windowHeight)) {
                                                TitleAction.SINGLEPLAYER -> enterGame()
                                                TitleAction.EXIT -> running = false
                                                TitleAction.MULTIPLAYER ->
                                                    println("Multiplayer: WIP — coming soon")
                                                TitleAction.NATIVE_MODS -> {
                                                    println("Opening Native Mods hub...")
                                                    ScreenManager.switchTo(Screen.NATIVE_MODS)
                                                }
                                                TitleAction.OPTIONS ->
                                                    println("Options: WIP — coming soon")
                                                null -> {}
                                            }
                                        }

                                        Screen.NATIVE_MODS -> {
                                            val mx = strata_get_mouse_x(window)
                                            val my = strata_get_mouse_y(window)
                                            when (NativeModsScreen.buttonAt(mx, my, windowWidth, windowHeight)) {
                                                ModsAction.EDITOR -> {
                                                    println("Opening mod editor...")
                                                    ScreenManager.switchTo(Screen.MOD_EDITOR)
                                                }
                                                ModsAction.MODS -> {
                                                    println("Opening mods list...")
                                                    ScreenManager.switchTo(Screen.MOD_LIST)
                                                }
                                                ModsAction.BACK -> {
                                                    ScreenManager.goToTitle()
                                                }
                                                null -> {}
                                            }
                                        }

                                        Screen.MOD_LIST -> {
                                            val mx = strata_get_mouse_x(window)
                                            val my = strata_get_mouse_y(window)
                                            val result = ModListScreen.hitTest(mx, my, windowWidth, windowHeight, modListScroll)
                                            when (result?.action) {
                                                ModListAction.TOGGLE_MOD -> {
                                                    val idx = result.modIndex
                                                    if (idx in modRegistry.enabled.indices) {
                                                        modRegistry.enabled[idx] = !modRegistry.enabled[idx]
                                                        println("Mod '${modRegistry.names[idx]}' ${if (modRegistry.enabled[idx]) "ENABLED" else "DISABLED"}")
                                                    }
                                                }
                                                ModListAction.BACK -> {
                                                    ScreenManager.switchTo(Screen.NATIVE_MODS)
                                                }
                                                null -> {}
                                            }
                                        }

                                        Screen.MOD_EDITOR -> {
                                            val mx = strata_get_mouse_x(window)
                                            val my = strata_get_mouse_y(window)
                                            when (ModEditorScreen.hitTest(mx, my, windowWidth, windowHeight)?.action) {
                                                EditorAction.SAVE -> {
                                                    webViewState.saveCurrentFile()
                                                    println("Editor: File saved")
                                                }
                                                EditorAction.FORMAT -> {
                                                    webViewState.formatCode()
                                                    println("Editor: Code formatted")
                                                }
                                                EditorAction.RUN -> {
                                                    println("Editor: Run mod (not implemented)")
                                                }
                                                EditorAction.BACK -> {
                                                    webViewState.hide()
                                                    ScreenManager.switchTo(Screen.NATIVE_MODS)
                                                }
                                                EditorAction.SETTINGS -> {
                                                    println("Editor: Settings (not implemented)")
                                                }
                                                null -> {}
                                            }
                                        }

                                        Screen.INVENTORY -> {
                                            // Clicking a hotbar slot selects it; clicking a
                                            // storage slot swaps it with the selected slot.
                                            val mx = strata_get_mouse_x(window)
                                            val my = strata_get_mouse_y(window)
                                            InventoryScreen.hotbarIndexAt(mx, my, windowWidth, windowHeight)
                                                ?.let { selectedSlot = it }
                                            val inv = inventory
                                            InventoryScreen.storageIndexAt(mx, my, windowWidth, windowHeight)
                                                ?.let { idx -> if (inv != null) inv.swap(idx, selectedSlot) }
                                        }

                                        Screen.GAME -> {
                                            // Spectators pass through the world without
                                            // touching it; chat typing swallows the mouse.
                                            if (!chatOpen && gameMode != GameMode.SPECTATOR) {
                                                player?.let { p ->
                                                    val cy = cos(camera.yaw); val sy = sin(camera.yaw)
                                                    val cp = cos(camera.pitch); val sp = sin(camera.pitch)
                                                    p.forward = Vec3(sy * cp, -sp, cy * cp)
                                                    val hit = p.removeTarget()
                                                    if (hit != null) {
                                                        streamer?.onBlockEdited(hit.block.x, hit.block.y, hit.block.z)
                                                        // Breaking blocks earns XP (survival only —
                                                        // creative/spectator don't level up).
                                                        if (gameMode == GameMode.SURVIVAL) {
                                                            val ups = survival.addXp(1f)
                                                            if (ups > 0) println("Level up! Now level ${survival.level}")
                                                        }
                                                        println("Block removed at ${hit.block}")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                SDL_MOUSE_BUTTON_RIGHT.toInt() -> {
                                    // Eating/drinking is survival-only; placing works in
                                    // survival + creative; spectator can't interact at all.
                                    if (ScreenManager.current == Screen.GAME && !chatOpen && gameMode != GameMode.SPECTATOR) {
                                        player?.let { p ->
                                            val cy = cos(camera.yaw); val sy = sin(camera.yaw)
                                            val cp = cos(camera.pitch); val sp = sin(camera.pitch)
                                            p.forward = Vec3(sy * cp, -sp, cy * cp)
                                            // Use the LIVE hotbar slot — it can be empty now.
                                            val item = inventory?.hotbar?.getOrNull(selectedSlot)
                                            if (item != null) {
                                                when {
                                                    item.food > 0 && gameMode == GameMode.SURVIVAL -> {
                                                        // Eat the food slot: restores hunger (bars animate back up)
                                                        survival.eat(item.food.toFloat())
                                                        println("Ate food (+${item.food} hunger, now ${survival.hunger.roundToInt()})")
                                                    }
                                                    item.drink > 0 && gameMode == GameMode.SURVIVAL -> {
                                                        // Drink the water bottle: restores thirst
                                                        survival.drink(item.drink.toFloat())
                                                        println("Drank water (+${item.drink} thirst, now ${survival.thirst.roundToInt()})")
                                                    }
                                                    item.block != null -> {
                                                        val hit = p.placeTarget(item.block)
                                                        if (hit != null) {
                                                            streamer?.onBlockEdited(hit.adjacent.x, hit.adjacent.y, hit.adjacent.z)
                                                            println("Placed ${item.block} at ${hit.adjacent}")
                                                        }
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* ── Game-mode logic only (streaming, movement, survival) ── */
            if (ScreenManager.current == Screen.GAME) {
                val s = streamer
                val p = player
                val w = world
                if (s != null && p != null && w != null) {
                    /* Stream chunks around the player; upload mesh only when dirty */
                    val pcx = w.worldToChunkX(floor(camera.x).toInt())
                    val pcy = w.worldToChunkY(floor(camera.y).toInt())
                    val pcz = w.worldToChunkZ(floor(camera.z).toInt())
                    s.update(pcx, pcy, pcz)

                    if (s.dirty && s.meshKeys().isNotEmpty()) {
                        requestMeshRebuild()
                    }

                    /* Take the finished background mesh and upload it to the GPU. */
                    pendingMesh?.let { d ->
                        if (d.isCompleted) {
                            val built = d.getCompleted()
                            pendingMesh = null
                            currentData = built.solid
                            currentVertexCount = currentData.size / 7
                            currentWaterData = built.water
                            currentWaterVertexCount = currentWaterData.size / 7
                            currentRanges = built.ranges
                            uploadVertexBuffer()
                            uploadWaterBuffer()
                            println("World mesh rebuilt: ${currentVertexCount} solid + ${currentWaterVertexCount} water verts across ${s.meshKeys().size} chunks (${currentRanges.size} ranges)")
                            // Rebuild only if genuinely new meshes landed while the
                            // build was running (epoch moved). Otherwise clear the flag
                            // so the whole-world rebuild storm stops at steady state.
                            if (s.meshEpoch() > pendingEpoch) {
                                requestMeshRebuild()
                            } else {
                                s.markMeshUploaded()
                            }
                        }
                    }

                    /* ── Fixed 60 Hz simulation steps ──
                     * Movement + survival advance at a constant rate; the
                     * accumulator guarantees exactly-once-per-FIXED_DT even if
                     * frames are uneven. Streaming stays per-frame above. */
                    fun held(scancode: Int): Boolean =
                        strata_key_down(scancode) || (scancode in keyState.indices && keyState[scancode])

                    while (accumulator >= FIXED_DT) {
                        accumulator -= FIXED_DT

                        /* WASD + yaw -> world-space movement direction. */
                        val cy = cos(camera.yaw); val sy = sin(camera.yaw)
                        var dx = 0f; var dz = 0f
                        if (held(SDL_SCANCODE_W)) { dx += sy; dz += cy }
                        if (held(SDL_SCANCODE_S)) { dx -= sy; dz -= cy }
                        if (held(SDL_SCANCODE_A)) { dx += cy; dz -= sy }
                        if (held(SDL_SCANCODE_D)) { dx -= cy; dz += sy }

                        /* Normalize horizontal movement so diagonal isn't faster */
                        val hLen = sqrt(dx * dx + dz * dz)
                        if (hLen > 0f) { dx /= hLen; dz /= hLen }

                        /* ── Player physics (fixed step), mode-aware: ──
                         *  survival   — gravity, AABB voxel collision, jump/sneak/swim
                         *  creative   — flight (no gravity), still blocked by walls
                         *  spectator  — no-clip flight straight through the world
                         * The camera is positioned at the player's eye after. ── */
                        p.tick(
                            FIXED_DT, dx, dz,
                            jump = held(SDL_SCANCODE_SPACE),
                            sneak = held(SDL_SCANCODE_LSHIFT),
                            mode = gameMode
                        )
                        camera = camera.copy(
                            x = p.position.x,
                            y = p.position.y + Player.EYE_HEIGHT,
                            z = p.position.z
                        )

                        /* ── Survival sim (fixed step) — survival mode only:
                         * hunger + thirst drain, regen requires BOTH well-fed
                         * and well-hydrated, drowning underwater, starvation +
                         * dehydration at empty bars. Health is double hearts
                         * (max 40) and bottoms out at 1 (gentle sim, no
                         * death). Creative/spectator are immortal. */
                        if (gameMode == GameMode.SURVIVAL) survival.tick(FIXED_DT, p.inWater)
                    }

                    /* ── Ease bar fill toward targets; ghost lags behind.
                     * This runs per-frame (not per fixed step) so the HUD
                     * animation stays smooth at any frame rate. ── */
                    val ease = (frameTime * 6f).coerceAtMost(1f)
                    val ghostEase = (frameTime * 2f).coerceAtMost(1f)
                    healthDisplay += (survival.health - healthDisplay) * ease
                    hungerDisplay += (survival.hunger - hungerDisplay) * ease
                    thirstDisplay += (survival.thirst - thirstDisplay) * ease
                    healthGhost += (survival.health - healthGhost) * ghostEase
                    hungerGhost += (survival.hunger - hungerGhost) * ghostEase
                    thirstGhost += (survival.thirst - thirstGhost) * ghostEase
                }
            }

            /* ── Build + upload the UI overlay (title / inventory / HUD) ── */
            if (ScreenManager.current == Screen.TITLE) {
                val titleState = TitleScreen.TitleState(
                    time = strata_get_ticks().toFloat() / 1000f,
                    mouseX = strata_get_mouse_x(window),
                    mouseY = strata_get_mouse_y(window)
                )
                val uiData = TitleScreen.build(titleState, windowWidth, windowHeight)
                if (uiData.isNotEmpty()) {
                    uiData.usePinned { pinned ->
                        strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (uiData.size / 7).toUInt())
                    }
                }                } else if (ScreenManager.current == Screen.NATIVE_MODS) {
                    // Use ImGui for Native Mods screen
                    if (!strataImGuiInitialized) {
                        strataImGuiInitialized = strata_cimgui.StrataImGui.init(
                            instance, selected, device, queueIndex, queue, surface, window, 2u
                        )
                    }
                    
                    if (strataImGuiInitialized) {
                        strata_cimgui.StrataImGui.newFrame()
                        
                        val action = strata_cimgui.StrataImGui.nativeModsScreen()
                        
                        if (action == 1) {
                            // EDITOR clicked
                            ScreenManager.switchTo(Screen.MOD_EDITOR)
                        } else if (action == 2) {
                            // MODS clicked
                            ScreenManager.switchTo(Screen.MOD_LIST)
                        } else if (action == 3) {
                            // BACK clicked
                            ScreenManager.switchTo(Screen.TITLE)
                        }
                        
                        // Prepare ImGui draw data for rendering inside the Vulkan render pass.
                        strata_cimgui.StrataImGui.prepareRender()
                    }
                } else if (ScreenManager.current == Screen.MOD_LIST) {
                    val modListState = ModListScreen.ModListState(
                        time = strata_get_ticks().toFloat() / 1000f,
                        mouseX = strata_get_mouse_x(window),
                        mouseY = strata_get_mouse_y(window),
                        modNames = modRegistry.names,
                        modEnabled = modRegistry.enabled,
                        scrollOffset = modListScroll
                    )
                    val listData = ModListScreen.build(modListState, windowWidth, windowHeight)
                    if (listData.isNotEmpty()) {
                        listData.usePinned { pinned ->
                            strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (listData.size / 7).toUInt())
                        }
                    }
                } else if (ScreenManager.current == Screen.MOD_EDITOR) {
                    // Initialize webview if not done yet
                    if (!webViewInitialized) {
                        webViewInitialized = webViewState.create(windowWidth, windowHeight, debug = true)
                        if (webViewInitialized) {
                            webViewState.show()
                            println("Mod Editor webview initialized")
                        } else {
                            println("WARNING: Failed to initialize Mod Editor webview")
                        }
                    }
                    
                    // Process webview events
                    webViewState.processEvents()
                    
                    // Build editor overlay (title bar, buttons, status bar)
                    val editorState = ModEditorScreen.EditorState(
                        time = strata_get_ticks().toFloat() / 1000f,
                        mouseX = strata_get_mouse_x(window),
                        mouseY = strata_get_mouse_y(window),
                        currentFile = webViewState.currentFile,
                        webviewReady = webViewState.isReady,
                        theme = webViewState.theme
                    )
                    val editorData = ModEditorScreen.build(editorState, windowWidth, windowHeight)
                    if (editorData.isNotEmpty()) {
                        editorData.usePinned { pinned ->
                            strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (editorData.size / 7).toUInt())
                        }
                    }
                } else if (ScreenManager.current == Screen.INVENTORY) {
                val inv = inventory
                if (inv != null) {
                    val invData = InventoryScreen.build(
                        InventoryScreen.InventoryState(
                            storage = inv.storage,
                            hotbar = inv.hotbar,
                            selectedSlot = selectedSlot,
                            mouseX = strata_get_mouse_x(window),
                            mouseY = strata_get_mouse_y(window)
                        ),
                        windowWidth, windowHeight
                    )
                    if (invData.isNotEmpty()) {
                        invData.usePinned { pinned ->
                            strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (invData.size / 7).toUInt())
                        }
                    }
                }
            } else {
                var hudData = Hud.build(
                    Hud.HudState(
                        camX = camera.x, camY = camera.y, camZ = camera.z,
                        yaw = camera.yaw,
                        fps = fps,
                        seed = world?.seed ?: 12345,
                        chunks = streamer?.meshKeys()?.size ?: 0,
                        health = survival.health.roundToInt(),
                        hunger = survival.hunger.roundToInt(),
                        thirst = survival.thirst.roundToInt(),
                        healthDisplay = healthDisplay,
                        hungerDisplay = hungerDisplay,
                        thirstDisplay = thirstDisplay,
                        healthGhost = healthGhost,
                        hungerGhost = hungerGhost,
                        thirstGhost = thirstGhost,
                        xp = survival.xpProgress,
                        xpLevel = survival.level,
                        selectedSlot = selectedSlot,
                        time = strata_get_ticks().toFloat() / 1000f,
                        hotbar = inventory?.hotbar ?: Hud.HOTBAR,
                        gamemode = gameMode,
                        gpuName = gpuName,
                        vulkanVersion = gpuVulkanVersion,
                        cpuLine = cpuLine,
                        osLine = osLine
                    ),
                    windowWidth, windowHeight
                )
                /* ── Chat overlay: the full typing box while open, otherwise a
                 * small recent-messages log in the corner. Both merge into the
                 * same HUD buffer. Suggestion selection is keyboard-only
                 * (Tab / Up / Down) — the mouse stays captured while typing,
                 * so the dropdown is not clickable. ── */
                if (chatOpen) {
                    val chatData = ChatOverlay.build(
                        ChatOverlay.ChatState(
                            lines = chat.lines,
                            input = chat.input,
                            suggestions = chatSuggestions,
                            selectedSuggestion = chatSuggestionIndex,
                            cursorVisible = (strata_get_ticks().toInt() / 500) % 2 == 0,
                            open = true
                        ),
                        windowWidth, windowHeight
                    )
                    if (chatData.isNotEmpty()) hudData += chatData
                } else if (chat.lines.isNotEmpty()) {
                    // Recent-messages log — only drawn once something was said,
                    // so an empty chat adds nothing to the HUD.
                    val chatData = ChatOverlay.build(
                        ChatOverlay.ChatState(
                            lines = chat.lines.takeLast(3),
                            input = "",
                            suggestions = emptyList(),
                            selectedSuggestion = 0,
                            cursorVisible = false,
                            open = false
                        ),
                        windowWidth, windowHeight
                    )
                    if (chatData.isNotEmpty()) hudData += chatData
                }

                if (hudData.isNotEmpty()) {
                    hudData.usePinned { pinned ->
                        strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (hudData.size / 7).toUInt())
                    }
                }
            }

            /* World uniforms: MVP(16) + camera pos(3) + time(1) = 80 bytes */
            val aspect = windowWidth.toFloat() / windowHeight.toFloat()
            val mvp = computeMVP(camera, aspect)
            val worldPC = FloatArray(20)
            mvp.copyInto(worldPC, 0)
            worldPC[16] = camera.x
            worldPC[17] = camera.y
            worldPC[18] = camera.z
            worldPC[19] = strata_get_ticks().toFloat() / 1000f
            /* UI draws straight in clip space, so its MVP is identity */
            val hudMvp = Mat4.identity().m

            /* Frustum culling: keep only chunks whose AABB intersects the
             * camera's view. Each kept chunk becomes 4 uint32s —
             * (solidStart, solidCount, waterStart, waterCount) — that the C
             * renderer draws via vkCmdDraw firstVertex offsets. Off-screen
             * chunks cost zero GPU work. */
            val frustum = Frustum(mvp)
            val visible = currentRanges.filter { r ->
                frustum.intersectsAabb(
                    r.minX, r.minY, r.minZ,
                    r.minX + chunkSize, r.minY + chunkSize.toFloat(), r.minZ + chunkSize
                )
            }
            val drawArray = UIntArray(visible.size * 4)
            var di = 0
            for (r in visible) {
                drawArray[di++] = r.solidStart.toUInt()
                drawArray[di++] = r.solidCount.toUInt()
                drawArray[di++] = r.waterStart.toUInt()
                drawArray[di++] = r.waterCount.toUInt()
            }

            worldPC.usePinned { wp ->
                hudMvp.usePinned { hp ->
                    if (drawArray.isNotEmpty()) {
                        drawArray.usePinned { dr ->
                            strata_render_frame(instance.handle, selected.handle, device, queue, swapchain, pipeline, wp.addressOf(0), waterPipeline, uiPipeline, hp.addressOf(0), dr.addressOf(0), drawArray.size.toUInt())
                        }
                    } else {
                        strata_render_frame(instance.handle, selected.handle, device, queue, swapchain, pipeline, wp.addressOf(0), waterPipeline, uiPipeline, hp.addressOf(0), null, 0u)
                    }
                }
            }
            // Inline screenshot result (set by render_frame after the readback).
            if (strata_capture_done(swapchain) != 0u) {
                println("Screenshot saved: $pendingShotName")
                strata_capture_ack(swapchain)
            }
            // Intel Xe fix: if the swapchain went out of date (resize, TDR,
            // minimize), rebuild immediately so the next frame has a valid
            // surface. Without this the screen freezes / goes black.
            if (strata_swapchain_needs_rebuild(swapchain) != 0u) {
                memScoped {
                    val pw = alloc<IntVar>()
                    val ph = alloc<IntVar>()
                    strata_get_window_size_pixels(window, pw.ptr, ph.ptr)
                    val w = if (pw.value > 0) pw.value else windowWidth
                    val h = if (ph.value > 0) ph.value else windowHeight
                    if (!rebuildSwapchain(w, h)) {
                        println("Swapchain rebuild failed — exiting game loop")
                        running = false
                    }
                }
            }

            SDL_Delay(16u) // ~60 FPS cap
        }

        /* ── ImGui Cleanup ── */
        if (strataImGuiInitialized) {
            strata_cimgui.StrataImGui.shutdown()
        }
        
        /* ── WebView Cleanup ── */
        if (webViewInitialized) {
            webViewState.destroy()
            println("WebView destroyed")
        }
        
        /* ── Main Cleanup ── */
        scope.cancel()
        strata_destroy_pipeline(instance.handle, device, pipeline)
        strata_destroy_pipeline(instance.handle, device, waterPipeline)
        strata_destroy_pipeline(instance.handle, device, uiPipeline)
        strata_destroy_swapchain(instance.handle, device, swapchain)
        strata_destroy_allocator()
        strata_destroy_device(instance.handle, device)
        strata_destroy_vulkan_surface(instance.handle, surface)
        instance.close()
    }
    vulkan.close()

    SDL_DestroyWindow(window)
    SDL_Quit()
}
