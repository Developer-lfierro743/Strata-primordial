1|1|1|package strata
2|2|2|
3|3|3|import io.technoirlab.volk.VK_QUEUE_GRAPHICS_BIT
4|4|4|import io.technoirlab.vulkan.ApplicationInfo
5|5|5|import io.technoirlab.vulkan.Vulkan
6|6|6|import kotlinx.cinterop.ExperimentalForeignApi
7|7|7|import kotlinx.cinterop.alloc
8|8|8|import kotlinx.cinterop.ptr
9|9|9|import kotlinx.cinterop.memScoped
10|10|10|import kotlinx.cinterop.addressOf
11|11|11|import kotlinx.cinterop.usePinned
12|12|12|import kotlinx.cinterop.get
13|13|13|import kotlinx.cinterop.set
14|14|14|import kotlinx.cinterop.toKString
15|15|15|import kotlinx.cinterop.IntVar
16|16|16|import kotlinx.cinterop.value
17|17|17|import kotlinx.coroutines.CoroutineScope
18|18|18|import kotlinx.coroutines.SupervisorJob
19|19|19|import kotlinx.coroutines.Dispatchers
20|20|20|import kotlinx.coroutines.Deferred
21|21|21|import kotlinx.coroutines.async
22|22|22|import kotlinx.coroutines.cancel
23|23|23|import kotlinx.coroutines.ExperimentalCoroutinesApi
24|24|24|import strata.sdl3.SDL_CreateWindow
25|25|25|import strata.sdl3.SDL_Delay
26|26|26|import strata.sdl3.SDL_DestroyWindow
27|27|27|import strata.sdl3.SDL_GetNumLogicalCPUCores
28|28|28|import strata.sdl3.SDL_GetPlatform
29|29|29|import strata.sdl3.SDL_GetPowerInfo
30|30|30|import strata.sdl3.SDL_GetSystemRAM
31|31|31|import strata.sdl3.SDL_Init
32|32|32|import strata.sdl3.SDL_Quit
33|33|33|import strata.sdl3.SDL_WINDOW_RESIZABLE
34|34|34|import strata.sdl3.strata_create_allocator
35|35|35|import strata.sdl3.strata_create_device
36|36|36|import strata.sdl3.strata_create_pipeline
37|37|37|import strata.sdl3.strata_create_ui_pipeline
38|38|38|import strata.sdl3.strata_create_water_pipeline
39|39|39|import strata.sdl3.strata_create_swapchain
40|40|40|import strata.sdl3.strata_create_vertex_buffer
41|41|41|import strata.sdl3.strata_create_water_buffer
42|42|42|import strata.sdl3.strata_create_vulkan_surface
43|43|43|import strata.sdl3.strata_destroy_allocator
44|44|44|import strata.sdl3.strata_destroy_device
45|45|45|import strata.sdl3.strata_destroy_pipeline
46|46|46|import strata.sdl3.strata_destroy_swapchain
47|47|47|import strata.sdl3.strata_destroy_vulkan_surface
48|48|48|import strata.sdl3.strata_get_device_queue
49|49|49|import strata.sdl3.strata_get_mouse_x
50|50|50|import strata.sdl3.strata_get_mouse_y
51|51|51|import strata.sdl3.strata_presentation_support
52|52|52|import strata.sdl3.strata_poll_input
53|53|53|import strata.sdl3.strata_render_frame
54|54|54|import strata.sdl3.strata_get_ticks
55|55|55|import strata.sdl3.strata_request_capture
56|56|56|import strata.sdl3.strata_capture_done
57|57|57|import strata.sdl3.strata_capture_ack
58|58|58|import strata.sdl3.strata_set_relative_mouse
59|59|59|import strata.sdl3.strata_key_down
60|60|60|import strata.sdl3.strata_set_dpi_aware
61|61|61|import strata.sdl3.strata_set_text_input
62|62|62|import strata.sdl3.strata_get_window_size_pixels
63|63|63|import strata.sdl3.strata_swapchain_needs_rebuild
64|64|65|
65|65|66|import kotlin.math.cos
66|66|67|import kotlin.math.sin
67|67|68|import kotlin.math.sqrt
68|68|69|import kotlin.math.floor
69|69|70|import kotlin.math.roundToInt
70|70|71|
71|71|72|private const val SDL_INIT_VIDEO = 0x00000020u
72|72|73|private const val SDL_WINDOW_VULKAN = 0x0000000010000000uL
73|73|74|
74|74|75|/* ── SDL3 event types (from SDL_events.h) ──
75|75|76| * IMPORTANT: these are the SDL3 values. SDL_EVENT_KEY_DOWN is 0x300 and
76|76|77| * SDL_EVENT_MOUSE_BUTTON_DOWN is 0x401 (NOT the SDL2-style 0x401/0x403). */
77|77|78|private const val SDL_EVENT_QUIT = 0x100
78|78|79|private const val SDL_EVENT_WINDOW_RESIZED = 0x206
79|79|80|private const val SDL_EVENT_KEY_DOWN = 0x300
80|80|81|private const val SDL_EVENT_KEY_UP = 0x301
81|81|82|private const val SDL_EVENT_TEXT_INPUT = 0x303
82|82|83|private const val SDL_EVENT_MOUSE_MOTION = 0x400
83|83|84|private const val SDL_EVENT_MOUSE_BUTTON_DOWN = 0x401
84|84|85|private const val SDL_EVENT_MOUSE_BUTTON_UP = 0x402
85|85|86|
86|86|87|private const val SDL_SCANCODE_W = 26
87|87|88|private const val SDL_SCANCODE_S = 22
88|88|89|private const val SDL_SCANCODE_A = 4
89|89|90|private const val SDL_SCANCODE_D = 7
90|90|91|private const val SDL_SCANCODE_T = 23   // opens chat
91|91|92|private const val SDL_SCANCODE_SPACE = 44
92|92|93|private const val SDL_SCANCODE_RETURN = 40
93|93|94|private const val SDL_SCANCODE_LSHIFT = 225
94|94|95|private const val SDL_SCANCODE_ESCAPE = 41
95|95|96|private const val SDL_SCANCODE_BACKSPACE = 42
96|96|97|private const val SDL_SCANCODE_TAB = 43
97|97|98|private const val SDL_SCANCODE_UP = 82
98|98|99|private const val SDL_SCANCODE_DOWN = 81
99|99|100|private const val SDL_SCANCODE_1 = 30
100|100|101|private const val SDL_SCANCODE_9 = 38   // 1..9 are contiguous (30..38)
101|101|102|private const val SDL_SCANCODE_0 = 39   // selects hotbar slot 10
102|102|103|private const val SDL_SCANCODE_MINUS = 45  // selects hotbar slot 11 (SDL3 value)
103|103|104|private const val SDL_SCANCODE_E = 8    // opens/closes the inventory
104|104|105|private const val SDL_SCANCODE_F2 = 59   // takes a screenshot (SDL3 value)
105|105|106|private const val SDL_MOUSE_BUTTON_LEFT = 1u
106|106|107|private const val SDL_MOUSE_BUTTON_RIGHT = 3u
107|107|108|
108|108|109|/** Format a packed VK_MAKE_API_VERSION value as "major.minor.patch". */
109|109|110|private fun formatVulkanVersion(v: UInt): String =
110|110|111|    "${(v shr 22) and 0x7Fu}.${(v shr 12) and 0x3FFu}.${v and 0xFFFu}"
111|111|112|
112|112|113|/* ── Application ──
113|113|114| * Camera + matrix math (Camera, Mat4, perspectiveMatrix, lookAtMatrix,
114|114|115| * computeMVP) lives in commonMain/CameraMath.kt; the world-buffer builder +
115|115|116| * material split (WorldMeshSplit) lives in commonMain/WorldMeshSplit.kt. */
116|116|117|
117|117|118|@OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)
118|118|119|fun main() {
119|119|120|    // DPI-aware FIRST: without this, Windows DPI-scales the window (logical
120|120|121|    // 1280x720 becomes a 1024x576 physical surface at 125%), which desyncs the
121|121|122|    // Vulkan swapchain size from the HUD coordinate space and pushes HUD
122|122|123|    // elements (hotbar, minimap) off-screen.
123|123|124|    strata_set_dpi_aware()
124|124|125|    check(SDL_Init(SDL_INIT_VIDEO)) { "SDL3 video initialization failed" }
125|125|126|
126|126|127|    val window = SDL_CreateWindow("Strata: Primordial", 1280, 720, SDL_WINDOW_RESIZABLE or SDL_WINDOW_VULKAN)
127|127|128|        ?: error("SDL3 window creation failed")
128|128|129|
129|129|130|    // The title screen needs a visible, free cursor; relative (locked) mouse
130|130|131|    // is only enabled once the player enters the game.
131|131|132|    if (!strata_set_relative_mouse(window, false)) {
132|132|133|        println("Warning: could not release relative mouse mode")
133|133|134|    }
134|134|135|
135|135|136|    println("Strata: Primordial SDL3 window created")
136|136|137|
137|137|138|    val vulkan = Vulkan()
138|138|139|    memScoped {
139|139|140|        val instance = vulkan.createInstance(
140|140|141|            enabledExtensions = listOf("VK_KHR_surface", "VK_KHR_win32_surface"),
141|141|142|            applicationInfo = ApplicationInfo(
142|142|143|                apiVersion = 0u,
143|143|144|                applicationName = "Strata: Primordial",
144|144|145|                engineName = "Strata Engine"
145|145|146|            )
146|146|147|        )
147|147|148|        println("Vulkan instance created: ${instance.handle}")
148|148|149|
149|149|150|        val surface = strata_create_vulkan_surface(window, instance.handle)
150|150|151|            ?: error("SDL3 Vulkan surface creation failed")
151|151|152|        println("SDL3 Vulkan surface created: $surface")
152|152|153|
153|153|154|        val devices = instance.enumeratePhysicalDevices()
154|154|155|        println("Physical devices: ${devices.size}")
155|155|156|        for (d in devices) {
156|156|157|            val props = d.getProperties()
157|157|158|            println("  ${props.deviceName?.toKString()}")
158|158|159|        }
159|159|160|
160|160|161|        val selected = devices.firstOrNull()
161|161|162|            ?: error("No Vulkan physical device found")
162|162|163|
163|163|164|        /* GPU identity for the HUD info panel (F3-style): deviceName is a C
164|164|165|         * string, apiVersion is packed VK_MAKE_API_VERSION. Captured once at
165|165|166|         * startup and copied to plain Kotlin Strings for the per-frame HUD. */
166|166|167|        val gpuProps = selected.getProperties()
167|167|168|        val gpuName = gpuProps.deviceName?.toKString() ?: "Unknown GPU"
168|168|169|        val gpuVulkanVersion = formatVulkanVersion(gpuProps.apiVersion)
169|169|170|        println("Selected: $gpuName (Vulkan $gpuVulkanVersion)")
170|170|171|
171|171|172|        /* System info — SDL3 native (no extra deps; DeviceKit/KDeviceInfo are
172|172|173|         * JVM/Android/iOS-only and can't link against this Kotlin/Native
173|173|174|         * target). CPU cores, RAM, OS and power state feed the HUD info
174|174|175|         * panel next to the GPU line. */
175|175|176|        val cpuCores = SDL_GetNumLogicalCPUCores()
176|176|177|        // Round to the nearest GB (SDL reports MB; a 16 GB machine reports ~16255).
177|177|178|        val ramGb = (SDL_GetSystemRAM() + 512) / 1024
178|178|179|        val platform = SDL_GetPlatform()?.toKString() ?: "Unknown"
179|179|180|        val powerText = memScoped {
180|180|181|            // SDL allows NULL for the seconds out-param — we only need the %.
181|181|182|            val pct = alloc<IntVar>()
182|182|183|            val st = SDL_GetPowerInfo(null, pct.ptr)
183|183|184|            // SDL3 SDL_PowerState: 1=ON_BATTERY 2=NO_BATTERY 3=CHARGING 4=CHARGED
184|184|185|            // (bound as Int because the enum includes -1 = ERROR)
185|185|186|            val label = when (st) {
186|186|187|                3 -> "CHARGING"; 4 -> "CHARGED"
187|187|188|                1 -> "ON BATTERY"; 2 -> "PLUGGED IN"
188|188|189|                else -> "UNKNOWN"
189|189|190|            }
190|190|191|            if (pct.value >= 0) "$label ${pct.value}%" else label
191|191|192|        }
192|192|193|        val cpuLine = "$cpuCores cores · $ramGb GB RAM"
193|193|194|        val osLine = "$platform · $powerText"
194|194|195|        println("System: $cpuLine | $osLine")
195|195|196|
196|196|197|        val queueFamilies = selected.getQueueFamilyProperties()
197|197|198|        val queueIndex = queueFamilies.indexOfFirst { props ->
198|198|199|            props.queueFlags and VK_QUEUE_GRAPHICS_BIT != 0u
199|199|200|        }
200|200|201|        check(queueIndex >= 0) { "No graphics queue family found" }
201|201|202|
202|202|203|        val presentOk = strata_presentation_support(instance.handle, selected.handle, queueIndex)
203|203|204|        println("Queue family $queueIndex: graphics + present = $presentOk")
204|204|205|
205|205|206|        val device = strata_create_device(instance.handle, selected.handle, queueIndex)
206|206|207|            ?: error("Failed to create logical device")
207|207|208|        println("Logical device created: $device")
208|208|209|
209|209|210|        val queue = strata_get_device_queue(instance.handle, device, queueIndex)
210|210|211|        println("Queue obtained: $queue")
211|211|212|
212|212|213|        /* ── VMA Allocator (replaces manual vkAllocateMemory calls) ── */
213|213|214|        check(strata_create_allocator(instance.handle, selected.handle, device)) { "Failed to create VMA allocator" }
214|214|215|        println("VMA allocator created")
215|215|216|
216|216|217|        /* ── Initial window size: use PHYSICAL PIXELS (SDL_GetWindowSizeInPixels).
217|217|218|         * The Vulkan swapchain and viewport are in pixels; under DPI scaling the
218|218|219|         * logical size (SDL_GetWindowSize) is smaller (e.g. 1024x576 vs 1280x720
219|219|220|         * at 125%), so the HUD must be laid out in the same pixel space as the
220|220|221|         * swapchain or its bottom elements get clipped off-screen. ── */
221|221|222|        var windowWidth = 1280
222|222|223|        var windowHeight = 720
223|223|224|        memScoped {
224|224|225|            val w = alloc<IntVar>()
225|225|226|            val h = alloc<IntVar>()
226|226|227|            strata_get_window_size_pixels(window, w.ptr, h.ptr)
227|227|228|            if (w.value > 0 && h.value > 0) {
228|228|229|                windowWidth = w.value
229|229|230|                windowHeight = h.value
230|230|231|            }
231|231|232|        }
232|232|233|        println("Window size (pixels): ${windowWidth}x${windowHeight}")
233|233|234|
234|234|235|        var swapchain = strata_create_swapchain(instance.handle, selected.handle, device, surface, queueIndex.toUInt(), windowWidth, windowHeight)
235|235|236|        checkNotNull(swapchain) { "Failed to create Vulkan swapchain" }
236|236|237|        println("Vulkan swapchain created successfully")
237|237|238|
238|238|239|        /* ── Create graphics pipeline (with MVP push constants) ── */
239|239|240|        var pipeline = strata_create_pipeline(instance.handle, selected.handle, device, swapchain)
240|240|241|        checkNotNull(pipeline) { "Failed to create Vulkan graphics pipeline" }
241|241|242|
242|242|243|        /* ── Transparent water pipeline: same vertex format, but alpha-blended
243|243|244|         * with the water shader pair (fresnel + animated surface). ── */
244|244|245|        var waterPipeline = strata_create_water_pipeline(instance.handle, selected.handle, device, swapchain)
245|245|246|            ?: error("Failed to create water pipeline")
246|246|247|        println("Water pipeline allocated (blend on)")
247|247|248|
248|248|249|        /* ── 2D UI overlay pipeline: same shaders, but NO depth test/write, ──
249|249|250|         * so overlay quads (title screen, HUD) always draw on top of the world. */
250|250|251|        var uiPipeline = strata_create_ui_pipeline(instance.handle, selected.handle, device, swapchain)
251|251|252|            ?: error("Failed to create UI overlay pipeline")
252|252|253|        println("UI overlay pipeline allocated (depth off)")
253|253|254|
254|254|255|        /* ── Game state (created lazily when the player clicks Singleplayer) ── */
255|255|256|        val chunkSize = 32
256|256|257|        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
257|257|258|        var world: World? = null
258|258|259|        var streamer: WorldStreamer? = null
259|259|260|        var player: Player? = null
260|260|261|        var inventory: PlayerInventory? = null
261|261|262|        var camera = Camera(x = 0f, y = 13f, z = 0f, yaw = 0f, pitch = 0f)
262|262|263|
263|263|264|        var currentData = FloatArray(0)
264|264|265|        var currentVertexCount = 0
265|265|266|        var currentWaterData = FloatArray(0)
266|266|267|        var currentWaterVertexCount = 0
267|267|268|        /** Per-chunk vertex ranges in [currentData]/[currentWaterData]; the
268|268|269|         * per-frame frustum culling filters this list before each draw. */
269|269|270|        var currentRanges: List<WorldMeshSplit.ChunkRange> = emptyList()
270|270|271|        var pendingMesh: Deferred<WorldMeshSplit.Split>? = null
271|271|272|        var pendingEpoch = -1
272|272|273|        var lastMeshRebuildTime = 0u // timestamp of last mesh rebuild request
273|273|274|        val MESH_REBUILD_COOLDOWN_MS = 100u // minimum ms between rebuilds
274|274|275|
275|275|276|        fun uploadVertexBuffer() {
276|276|277|            // Empty world buffer (title screen, or world not built yet): nothing
277|277|278|            // to upload — addressOf(0) on an empty array would crash on resize.
278|278|279|            if (currentVertexCount == 0) return
279|279|280|            currentData.usePinned { pinned ->
280|280|281|                strata_create_vertex_buffer(instance.handle, selected.handle, device, pipeline, pinned.addressOf(0), currentVertexCount.toUInt())
281|281|282|            }
282|282|283|        }
283|283|284|
284|284|285|        fun uploadWaterBuffer() {
285|285|286|            if (currentWaterVertexCount == 0) return
286|286|287|            currentWaterData.usePinned { pinned ->
287|287|288|                strata_create_water_buffer(instance.handle, selected.handle, device, waterPipeline, pinned.addressOf(0), currentWaterVertexCount.toUInt())
288|288|289|            }
289|289|290|        }
290|290|291|
291|291|292|        /**
292|292|293|         * Kick off the whole-world mesh concat on a background thread. The
293|293|294|         * FloatArrays in each ChunkMesh are immutable snapshots, so this is
294|294|295|         * safe to do off the render thread — the render loop never stalls on it.
295|295|296|         * The build also splits the combined buffer into opaque + water.
296|296|297|         */
297|297|298|        fun requestMeshRebuild() {
298|298|299|            val s = streamer ?: return
299|299|300|            if (pendingMesh != null) return
300|300|301|            // Cooldown: don't rebuild more often than every MESH_REBUILD_COOLDOWN_MS
301|301|302|            val now = strata_get_ticks()
302|302|303|            if (now - lastMeshRebuildTime < MESH_REBUILD_COOLDOWN_MS) return
303|303|304|            lastMeshRebuildTime = now
304|304|305|            pendingEpoch = s.meshEpoch()
305|305|306|            
306|306|307|            // Use incremental update if we have existing ranges and only some chunks changed
307|307|308|            val dirtyKeys = s.getDirtyChunks()
308|308|309|            if (currentRanges.isNotEmpty() && dirtyKeys.isNotEmpty() && dirtyKeys.size < currentRanges.size / 2) {
309|309|310|                // Incremental update: only rebuild changed chunks
310|310|311|                val newMeshes = dirtyKeys.mapNotNull { key ->
311|311|312|                    s.meshFor(key)?.let { key to it }
312|312|313|                }.toMap()
313|313|314|                pendingMesh = scope.async(Dispatchers.Default) {
314|314|315|                    val currentSplit = WorldMeshSplit.Split(currentData, currentWaterData, currentRanges)
315|315|316|                    val result = WorldMeshSplit.incrementalUpdate(currentSplit, dirtyKeys, newMeshes, chunkSize)
316|316|317|                    WorldMeshSplit.Split(result.solid, result.water, result.ranges)
317|317|318|                }
318|318|319|            } else {
319|319|320|                // Full rebuild: either first time, or too many chunks changed
320|320|321|                val entries = s.meshKeys().map { it to s.meshFor(it)!! }
321|321|322|                pendingMesh = scope.async(Dispatchers.Default) {
322|322|323|                    WorldMeshSplit.split(entries, chunkSize)
323|323|324|                }
324|324|325|            }
325|325|326|        }
326|326|327|
327|327|328|        /** Synchronous one-shot build used to fill the first frame of the game. */
328|328|329|        fun rebuildMeshNow() {
329|329|330|            val s = streamer ?: return
330|330|331|            val split = WorldMeshSplit.split(s.meshKeys().map { it to s.meshFor(it)!! }, chunkSize)
331|331|332|            currentData = split.solid
332|332|333|            currentVertexCount = currentData.size / 7
333|333|334|            currentWaterData = split.water
334|334|335|            currentWaterVertexCount = currentWaterData.size / 7
335|335|336|            currentRanges = split.ranges
336|336|337|            uploadVertexBuffer()
337|337|338|            uploadWaterBuffer()
338|338|339|            println("World mesh rebuilt: ${currentVertexCount} solid + ${currentWaterVertexCount} water verts across ${s.meshKeys().size} chunks (${currentRanges.size} ranges)")
339|339|340|        }
340|340|341|
341|341|342|        /* ── Survival state (drives the animated HUD bars) ──
342|342|343|         * Truth lives in [Survival] (double hearts, hunger, thirst, XP); the
343|343|344|         * display/ghost values below are eased toward it per frame so the
344|344|345|         * bars animate smoothly (Minecraft damage-trail style). */
345|345|346|        var selectedSlot = 0
346|346|347|        val survival = Survival()
347|347|348|        var healthDisplay = survival.health
348|348|349|        var hungerDisplay = survival.hunger
349|349|350|        var thirstDisplay = survival.thirst
350|350|351|        var healthGhost = survival.health
351|351|352|        var hungerGhost = survival.hunger
352|352|353|        var thirstGhost = survival.thirst
353|353|354|
354|354|355|        /* ── Game mode (survival / creative / spectator) ──
355|355|356|         * Switched with /gamemode in chat. Drives the player physics (walk vs
356|356|357|         * fly vs no-clip), the survival sim (only in survival) and which HUD
357|357|358|         * elements draw. */
358|358|359|        var gameMode = GameMode.SURVIVAL
359|359|360|
360|360|361|        /* ── Chat system (T to open) ──
361|361|362|         * Text arrives as SDL_EVENT_TEXT_INPUT events (the OS delivers real
362|362|363|         * characters, shift-aware); commands run through [ChatCommands] with
363|363|364|         * live autocomplete suggestions rendered by [ChatOverlay]. */
364|364|365|        val chat = ChatLog()
365|365|366|        var chatOpen = false
366|366|367|        var chatSuggestions: List<String> = emptyList()
367|367|368|        var chatSuggestionIndex = 0
368|368|369|
369|369|370|        fun refreshChatSuggestions() {
370|370|371|            chatSuggestions = ChatCommands.complete(chat.input)
371|371|372|            if (chatSuggestionIndex >= chatSuggestions.size) chatSuggestionIndex = 0
372|372|373|        }
373|373|374|
374|374|375|        fun openChat() {
375|375|376|            if (chatOpen || ScreenManager.current != Screen.GAME) return
376|376|377|            chatOpen = true
377|377|378|            chat.historyIndex = -1
378|378|379|            chatSuggestionIndex = 0
379|379|380|            refreshChatSuggestions()
380|380|381|            strata_set_text_input(window, true)
381|381|382|        }
382|382|383|
383|383|384|        fun closeChat() {
384|384|385|            if (!chatOpen) return
385|385|386|            chatOpen = false
386|386|387|            strata_set_text_input(window, false)
387|387|388|        }
388|388|389|
389|389|390|        /** Send the current input: commands execute, everything else is chat. */
390|390|391|        fun submitChat() {
391|391|392|            val inputText = chat.input.trim()
392|392|393|            chat.commitInput() // Routes through security bridge internally
393|393|394|            closeChat()
394|394|395|            if (inputText.isBlank()) return
395|395|396|            if (inputText.startsWith("/")) {
396|396|397|                val result = ChatCommands.execute(
397|397|398|                    inputText,
398|398|399|                    ChatCommands.Context(
399|399|400|                        seed = world?.seed ?: 12345,
400|400|401|                        setGameMode = { gameMode = it },
401|401|402|                        playerId = chat.playerId
402|402|403|                    )
403|403|404|                )
404|404|405|                when (result) {
405|405|406|                    null -> {} // Already handled by commitInput
406|406|407|                    is ChatCommands.Result.Message -> chat.addLine(result.text, system = true)
407|407|408|                    ChatCommands.Result.ClearChat -> chat.clear()
408|408|409|                    ChatCommands.Result.Unknown ->
409|409|410|                        chat.addLine("Unknown command — type /help for commands.", system = true)
410|410|411|                }
411|411|412|            }
412|412|413|            // Normal messages are already added by commitInput() through the security bridge
413|413|414|        }
414|414|415|
415|415|416|        /* ── Mod registry state ── */
416|416|417|        val modRegistry = object {
417|417|418|            val names = mutableListOf("ExampleMod", "TerrainPlus", "SoundPack", "BetterLighting")
418|418|419|            val enabled = mutableListOf(true, false, true, false)
419|419|420|        }
420|420|421|        var modListScroll = 0
421|421|422|        
422|422|423|423|423|426|
424|424|427|        /* ── Track keyboard state for smooth movement ── */
425|425|428|        val keyState = BooleanArray(512)
426|426|429|
427|427|430|        /**
428|428|431|         * Build the world, pre-warm it so the first frame is fully solid
429|429|432|         * (no gaps), and switch to the GAME screen. Runs once on the
430|430|433|         * Singleplayer button.
431|431|434|         */
432|432|435|        fun enterGame() {
433|433|436|            if (ScreenManager.current == Screen.GAME) return
434|434|437|            println("Starting singleplayer world (seed 12345)...")
435|435|438|            val w = World(seed = 12345, size = chunkSize)
436|436|439|            val st = WorldStreamer(
437|437|440|                w,
438|438|441|                renderDistance = 3,
439|439|442|                verticalRenderDistance = 2,
440|440|443|                scope = scope,
441|441|444|                genWorkerCount = (cpuCores / 2).coerceIn(2, 8),
442|442|445|                meshWorkerCount = cpuCores.coerceIn(2, 12)
443|443|446|            )
444|444|447|            println("WorldStreamer: ${st.genWorkerCount} gen workers, ${st.meshWorkerCount} mesh workers")
445|445|448|            world = w
446|446|449|            streamer = st
447|447|450|
448|448|451|            val prewarmStart = strata_get_ticks()
449|449|452|            // Compute where the player will land BEFORE pre-warming.
450|450|453|            val groundY = Terrain.heightAt(w.seed, 0, 0).coerceAtLeast(Terrain.SEA_LEVEL) + 1f
451|451|454|            val spawnCy = w.worldToChunkY(groundY.toInt())
452|452|455|            // Pre-warm 2 horizontal layers: the player's chunk and one below
453|453|456|            // (for collision when falling). Only wait for BLOCK DATA (not mesh)
454|454|457|            // so the player doesn't fall through unloaded chunks.
455|455|458|            var prewarmTicks = 0
456|456|459|            while (prewarmTicks < 3000) {
457|457|460|                st.update(w.worldToChunkX(0), spawnCy, w.worldToChunkZ(0))
458|458|461|                // Also trigger generation of the layer below for safety
459|459|462|                st.update(w.worldToChunkX(0), spawnCy - 1, w.worldToChunkZ(0))
460|460|463|                var blocksReady = true
461|461|464|                for (dx in -st.renderDistance..st.renderDistance) {
462|462|465|                    for (dz in -st.renderDistance..st.renderDistance) {
463|463|466|                        val key0 = w.chunkKey(w.worldToChunkX(0) + dx, spawnCy, w.worldToChunkZ(0) + dz)
464|464|467|                        val key1 = w.chunkKey(w.worldToChunkX(0) + dx, spawnCy - 1, w.worldToChunkZ(0) + dz)
465|465|468|                        if (!w.hasChunk(key0) || !w.hasChunk(key1)) {
466|466|469|                            blocksReady = false
467|467|470|                            break
468|468|471|                        }
469|469|472|                    }
470|470|473|                    if (!blocksReady) break
471|471|474|                }
472|472|475|                if (blocksReady) break
473|473|476|                SDL_Delay(20u)
474|474|477|                prewarmTicks++
475|475|478|            }
476|476|479|            println("Pre-warmed ${w.loadedKeys.size} chunks (${st.meshKeys().size} meshed) in ${(strata_get_ticks() - prewarmStart).toFloat() / 1000f}s")
477|477|480|
478|478|481|            if (st.meshKeys().isNotEmpty()) {
479|479|482|                rebuildMeshNow()
480|480|483|                st.markMeshUploaded()
481|481|484|            }
482|482|485|
483|483|486|            /* Place the player's FEET on the ground: heightAt + 1 sits on top
484|484|487|             * of the surface block (or just above sea level if in a lake).
485|485|488|             * The camera then rides at eye height (+1.62). */
486|486|489|            player = Player(w).also { it.position = Vec3(0f, groundY, 0f) }
487|487|490|            camera = Camera(x = 0f, y = groundY + Player.EYE_HEIGHT, z = 0f, yaw = 0f, pitch = 0f)
488|488|491|            inventory = PlayerInventory()
489|489|492|
490|490|493|            if (!strata_set_relative_mouse(window, true)) {
491|491|494|                println("Warning: relative mouse mode failed (look may be limited)")
492|492|495|            }
493|493|496|            ScreenManager.switchTo(Screen.GAME)
494|494|497|            println("Controls: WASD=move, Space=up, Shift=down, Mouse=look, LeftClick=break, RightClick=place, 1-9/0/-=slot, E=inventory, ESC=quit")
495|495|498|        }
496|496|499|
497|497|500|        /* ── Inventory open/close: E toggles it, the cursor is freed while
498|498|501|         * the inventory is open so slots can be clicked. ── */
499|499|502|        fun openInventory() {
500|500|503|            if (ScreenManager.current != Screen.GAME) return
501|501|504|            ScreenManager.switchTo(Screen.INVENTORY)
502|502|505|            strata_set_relative_mouse(window, false)
503|503|506|        }
504|504|507|        fun closeInventory() {
505|505|508|            if (ScreenManager.current != Screen.INVENTORY) return
506|506|509|            ScreenManager.switchTo(Screen.GAME)
507|507|510|            strata_set_relative_mouse(window, true)
508|508|511|        }
509|509|512|
510|510|513|        /* ── Helper: rebuild swapchain + pipeline after resize ── */
511|511|514|        fun rebuildSwapchain(newWidth: Int, newHeight: Int): Boolean {
512|512|515|            if (newWidth <= 0 || newHeight <= 0) return false
513|513|516|            println("Rebuilding swapchain: ${newWidth}x${newHeight}")
514|514|517|
515|515|518|            strata_destroy_pipeline(instance.handle, device, pipeline)
516|516|519|            strata_destroy_pipeline(instance.handle, device, waterPipeline)
517|517|520|            strata_destroy_pipeline(instance.handle, device, uiPipeline)
518|518|521|            strata_destroy_swapchain(instance.handle, device, swapchain)
519|519|522|
520|520|523|            windowWidth = newWidth
521|521|524|            windowHeight = newHeight
522|522|525|
523|523|526|            swapchain = strata_create_swapchain(instance.handle, selected.handle, device, surface, queueIndex.toUInt(), windowWidth, windowHeight)
524|524|527|            if (swapchain == null) {
525|525|528|                println("WARNING: Failed to recreate Vulkan swapchain — device may be lost")
526|526|529|                return false
527|527|530|            }
528|528|531|            pipeline = strata_create_pipeline(instance.handle, selected.handle, device, swapchain)
529|529|532|                ?: run { println("WARNING: Failed to recreate graphics pipeline"); return false }
530|530|533|            waterPipeline = strata_create_water_pipeline(instance.handle, selected.handle, device, swapchain)
531|531|534|                ?: run { println("WARNING: Failed to recreate water pipeline"); return false }
532|532|535|            uiPipeline = strata_create_ui_pipeline(instance.handle, selected.handle, device, swapchain)
533|533|536|                ?: run { println("WARNING: Failed to recreate UI pipeline"); return false }
534|534|537|
535|535|538|            uploadVertexBuffer()
536|536|539|            uploadWaterBuffer()
537|537|540|            println("Swapchain rebuilt: ${windowWidth}x${windowHeight}")
538|538|541|            return true
539|539|542|        }
540|540|543|
541|541|544|        /* ── Fixed timestep (formula #26: "fixing lag problems with fixed
542|542|545|         * timestep"). Game simulation steps at a constant 60 Hz regardless of
543|543|546|         * render frame rate, so movement and survival never speed up or jitter
544|544|547|         * when a frame hiccups — the accumulator absorbs the variance instead.
545|545|548|         * Per-frame work (streaming, mesh uploads, HUD) still runs every frame.
546|546|549|         * ── */
547|547|550|        val FIXED_DT = 1f / 60f
548|548|551|        val MAX_FRAME_TIME = 0.25f   // clamp huge hitches so we don't spiral
549|549|552|
550|550|553|        var running = true
551|551|554|        var lastTime = strata_get_ticks()
552|552|555|        var accumulator = 0f
553|553|556|        var fps = 60
554|554|557|        var frameCount = 0
555|555|558|        var fpsLastTick = strata_get_ticks()
556|556|559|        /* Name of the screenshot currently being captured inline by render_frame. */
557|557|560|        var pendingShotName = ""
558|558|561|        var strataImGuiInitialized = false
559|559|562|        println("Main loop entered")
560|560|563|        while (running) {
561|561|564|            val currentTime = strata_get_ticks()
562|562|565|            var frameTime = (currentTime - lastTime).toFloat() / 1000f
563|563|566|            lastTime = currentTime
564|564|567|            if (frameTime > MAX_FRAME_TIME) frameTime = MAX_FRAME_TIME
565|565|568|            accumulator += frameTime
566|566|569|            // Don't bank time while not in the game or typing in chat —
567|567|570|            // otherwise opening the inventory/chat would unleash a catch-up
568|568|571|            // burst of movement ticks.
569|569|572|            if (ScreenManager.current != Screen.GAME || chatOpen) accumulator = 0f
570|570|573|
571|571|574|            /* Track FPS (updated twice a second for the HUD) */
572|572|575|            frameCount++
573|573|576|            if (currentTime - fpsLastTick >= 500u) {
574|574|577|                fps = (frameCount * 1000) / (currentTime - fpsLastTick).toInt()
575|575|578|                frameCount = 0
576|576|579|                fpsLastTick = currentTime
577|577|580|            }
578|578|581|
579|579|582|            /* ── Poll input events (pass window for resize queries) ── */
580|580|583|            memScoped {
581|581|584|                val inputEvent = alloc<strata.sdl3.StrataInputEvent>()
582|582|585|                while (strata_poll_input(inputEvent.ptr, window)) {
583|583|586|                    // Forward SDL events to ImGui when it's active
584|584|587|                    if (strataImGuiInitialized && ScreenManager.current == Screen.NATIVE_MODS) {
585|586|589|                    }
586|587|590|                    
587|588|591|                    when (inputEvent.eventType) {
588|589|592|                        SDL_EVENT_QUIT -> running = false
589|590|593|
590|591|594|                        SDL_EVENT_WINDOW_RESIZED -> {
591|592|595|                            // New dimensions come from the event struct (set by C helper).
592|593|596|                            // The C helper reports LOGICAL size; convert to pixels so the
593|594|597|                            // swapchain and HUD stay in the same coordinate space.
594|595|598|                            val newW = inputEvent.windowWidth
595|596|599|                            val newH = inputEvent.windowHeight
596|597|600|                            if (newW > 0 && newH > 0) {
597|598|601|                                memScoped {
598|599|602|                                    val pw = alloc<IntVar>()
599|600|603|                                    val ph = alloc<IntVar>()
600|601|604|                                    strata_get_window_size_pixels(window, pw.ptr, ph.ptr)
601|602|605|                                    rebuildSwapchain(if (pw.value > 0) pw.value else newW, if (ph.value > 0) ph.value else newH)
602|603|606|                                }
603|604|607|                            }
604|605|608|                        }
605|606|609|
606|607|610|                        SDL_EVENT_KEY_DOWN -> {
607|608|611|                            val sc = inputEvent.keySymbol
608|609|612|                            // F2 screenshot works everywhere, even while typing.
609|610|613|                            if (sc == SDL_SCANCODE_F2) {
610|611|614|                                // Request an INLINE screenshot: render_frame reads
611|612|615|                                // back the CURRENT app-owned image (a presented
612|613|616|                                // image belongs to the display engine — reading it
613|614|617|                                // back is a spec violation that hangs Intel's GPU),
614|615|618|                                // writes the BMP and sets captureResult, which we
615|616|619|                                // check right after this frame renders.
616|617|620|                                val shotName = "screenshot_${strata_get_ticks()}.bmp"
617|618|621|                                strata_request_capture(swapchain, shotName)
618|619|622|                                pendingShotName = shotName
619|620|623|                            } else if (chatOpen) {
620|621|624|                                // While typing, every key goes to the chat box.
621|622|625|                                when (sc) {
622|623|626|                                    SDL_SCANCODE_ESCAPE -> closeChat()
623|624|627|                                    SDL_SCANCODE_RETURN -> submitChat()
624|625|628|                                    SDL_SCANCODE_BACKSPACE -> {
625|626|629|                                        chat.input = chat.input.dropLast(1)
626|627|630|                                        refreshChatSuggestions()
627|628|631|                                    }
628|629|632|                                    SDL_SCANCODE_TAB -> {
629|630|633|                                        // Accept the highlighted suggestion (Minecraft Tab).
630|631|634|                                        if (chatSuggestions.isNotEmpty()) {
631|632|635|                                            val pick = chatSuggestions[chatSuggestionIndex.coerceIn(0, chatSuggestions.size - 1)]
632|633|636|                                            chat.input = ChatCommands.applySuggestion(chat.input, pick) + " "
633|634|637|                                            refreshChatSuggestions()
634|635|638|                                        }
635|636|639|                                    }
636|637|640|                                    SDL_SCANCODE_UP -> {
637|638|641|                                        if (chatSuggestions.isNotEmpty()) {
638|639|642|                                            chatSuggestionIndex = (chatSuggestionIndex - 1 + chatSuggestions.size) % chatSuggestions.size
639|640|643|                                        } else {
640|641|644|                                            chat.recallUp()?.let {
641|642|645|                                                chat.input = it
642|643|646|                                                refreshChatSuggestions()
643|644|647|                                            }
644|645|648|                                        }
645|646|649|                                    }
646|647|650|                                    SDL_SCANCODE_DOWN -> {
647|648|651|                                        if (chatSuggestions.isNotEmpty()) {
648|649|652|                                            chatSuggestionIndex = (chatSuggestionIndex + 1) % chatSuggestions.size
649|650|653|                                        } else {
650|651|654|                                            chat.input = chat.recallDown() ?: ""
651|652|655|                                            refreshChatSuggestions()
652|653|656|                                        }
653|654|657|                                    }
654|655|658|                                }
655|656|659|                            } else {
656|657|660|                                if (sc == SDL_SCANCODE_ESCAPE) {
657|658|661|                                    // ESC: close sub-screens first, then inventory, then quit.
658|659|662|                                    when (ScreenManager.current) {
659|660|663|                                        Screen.MOD_LIST, Screen.MOD_EDITOR -> {
660|661|664|                                            println("Back to Native Mods hub")
661|662|665|                                            ScreenManager.switchTo(Screen.NATIVE_MODS)
662|663|666|                                        }
663|664|667|                                        Screen.NATIVE_MODS -> {
664|665|668|                                            println("Back to title screen")
665|666|669|                                            ScreenManager.goToTitle()
666|667|670|                                        }
667|668|671|                                        Screen.INVENTORY -> closeInventory()
668|669|672|                                        else -> running = false
669|670|673|                                    }
670|671|674|                                }
671|672|675|                                if (sc == SDL_SCANCODE_E) {
672|673|676|                                    // E toggles the inventory (in-game only).
673|674|677|                                    when (ScreenManager.current) {
674|675|678|                                        Screen.GAME -> openInventory()
675|676|679|                                        Screen.INVENTORY -> closeInventory()
676|677|680|                                        else -> {}
677|678|681|                                    }
678|679|682|                                }
679|680|683|                                if (sc == SDL_SCANCODE_T && ScreenManager.current == Screen.GAME) {
680|681|684|                                    openChat()
681|682|685|                                }
682|683|686|                                if (ScreenManager.current == Screen.TITLE) {
683|684|687|                                    // Enter / Space start a singleplayer world.
684|685|688|                                    if (sc == SDL_SCANCODE_RETURN || sc == SDL_SCANCODE_SPACE) enterGame()
685|686|689|                                } else if (ScreenManager.current == Screen.MOD_LIST) {
686|687|690|                                    // Up/Down arrows scroll the mod list.
687|688|691|                                    when (sc) {
688|689|692|                                        SDL_SCANCODE_UP -> modListScroll = (modListScroll - 1).coerceAtLeast(0)
689|690|693|                                        SDL_SCANCODE_DOWN -> modListScroll = (modListScroll + 1)
690|691|694|                                            .coerceAtMost(maxOf(0, modRegistry.names.size - 12))
691|692|695|                                    }
692|693|696|                                } else {
693|694|697|                                    // Hotbar hotkeys: 1-9, 0 and - select slots 0..10.
694|695|698|                                    when {
695|696|699|                                        sc in SDL_SCANCODE_1..SDL_SCANCODE_9 -> selectedSlot = sc - SDL_SCANCODE_1
696|697|700|                                        sc == SDL_SCANCODE_0 -> selectedSlot = 9
697|698|701|                                        sc == SDL_SCANCODE_MINUS -> selectedSlot = 10
698|699|702|                                    }
699|700|703|                                }
700|701|704|                            }
701|702|705|                            if (sc in keyState.indices) keyState[sc] = true
702|703|706|                        }
703|704|707|
704|705|708|                        SDL_EVENT_KEY_UP -> {
705|706|709|                            val sc = inputEvent.keySymbol
706|707|710|                            if (sc in keyState.indices) keyState[sc] = false
707|708|711|                        }
708|709|712|
709|710|713|                        SDL_EVENT_TEXT_INPUT -> {
710|711|714|                            // The OS delivers real typed characters (shift-aware,
711|712|715|                            // punctuation) straight into the chat box.
712|713|716|                            if (chatOpen) {
713|714|717|                                val text = inputEvent.text?.toKString() ?: ""
714|715|718|                                if (text.isNotEmpty()) {
715|716|719|                                    chat.input += text
716|717|720|                                    refreshChatSuggestions()
717|718|721|                                }
718|719|722|                            }
719|720|723|                        }
720|721|724|
721|722|725|                        SDL_EVENT_MOUSE_MOTION -> {
722|723|726|                            // Camera look only applies in-game (cursor is locked there),
723|724|727|                            // and not while the chat box is capturing the keyboard.
724|725|728|                            if (ScreenManager.current == Screen.GAME && !chatOpen) {
725|726|729|                                val sensitivity = 0.003f
726|727|730|                                camera = camera.copy(
727|728|731|                                    yaw = camera.yaw - inputEvent.mouseDx * sensitivity,
728|729|732|                                    pitch = (camera.pitch + inputEvent.mouseDy * sensitivity).coerceIn(-1.5f, 1.5f)
729|730|733|                                )
730|731|734|                            }
731|732|735|                        }
732|733|736|
733|734|737|                        SDL_EVENT_MOUSE_BUTTON_DOWN -> {
734|735|738|                            when (inputEvent.mouseButton) {
735|736|739|                                SDL_MOUSE_BUTTON_LEFT.toInt() -> {
736|737|740|                                    when (ScreenManager.current) {
737|738|741|                                        Screen.TITLE -> {
738|739|742|                                            // Title menu: hit-test the buttons.
739|740|743|                                            val mx = strata_get_mouse_x(window)
740|741|744|                                            val my = strata_get_mouse_y(window)
741|742|745|                                            when (TitleScreen.buttonAt(mx, my, windowWidth, windowHeight)) {
742|743|746|                                                TitleAction.SINGLEPLAYER -> enterGame()
743|744|747|                                                TitleAction.EXIT -> running = false
744|745|748|                                                TitleAction.MULTIPLAYER ->
745|746|749|                                                    println("Multiplayer: WIP — coming soon")
746|747|750|                                                TitleAction.NATIVE_MODS -> {
747|748|751|                                                    println("Opening Native Mods hub...")
748|749|752|                                                    ScreenManager.switchTo(Screen.NATIVE_MODS)
749|750|753|                                                }
750|751|754|                                                TitleAction.OPTIONS ->
751|752|755|                                                    println("Options: WIP — coming soon")
752|753|756|                                                null -> {}
753|754|757|                                            }
754|755|758|                                        }
755|756|759|
756|757|760|                                        Screen.NATIVE_MODS -> {
757|758|761|                                            val mx = strata_get_mouse_x(window)
758|759|762|                                            val my = strata_get_mouse_y(window)
759|760|763|                                            when (NativeModsScreen.buttonAt(mx, my, windowWidth, windowHeight)) {
760|761|764|                                                ModsAction.EDITOR -> {
761|762|765|                                                    println("Opening mod editor...")
762|763|766|                                                    ScreenManager.switchTo(Screen.MOD_EDITOR)
763|764|767|                                                }
764|765|768|                                                ModsAction.MODS -> {
765|766|769|                                                    println("Opening mods list...")
766|767|770|                                                    ScreenManager.switchTo(Screen.MOD_LIST)
767|768|771|                                                }
768|769|772|                                                ModsAction.BACK -> {
769|770|773|                                                    ScreenManager.goToTitle()
770|771|774|                                                }
771|772|775|                                                null -> {}
772|773|776|                                            }
773|774|777|                                        }
774|775|778|
775|776|779|                                        Screen.MOD_LIST -> {
776|777|780|                                            val mx = strata_get_mouse_x(window)
777|778|781|                                            val my = strata_get_mouse_y(window)
778|779|782|                                            val result = ModListScreen.hitTest(mx, my, windowWidth, windowHeight, modListScroll)
779|780|783|                                            when (result?.action) {
780|781|784|                                                ModListAction.TOGGLE_MOD -> {
781|782|785|                                                    val idx = result.modIndex
782|783|786|                                                    if (idx in modRegistry.enabled.indices) {
783|784|787|                                                        modRegistry.enabled[idx] = !modRegistry.enabled[idx]
784|785|788|                                                        println("Mod '${modRegistry.names[idx]}' ${if (modRegistry.enabled[idx]) "ENABLED" else "DISABLED"}")
785|786|789|                                                    }
786|787|790|                                                }
787|788|791|                                                ModListAction.BACK -> {
788|789|792|                                                    ScreenManager.switchTo(Screen.NATIVE_MODS)
789|790|793|                                                }
790|791|794|                                                null -> {}
791|792|795|                                            }
792|793|796|                                        }
793|794|797|
794|795|798|                                        Screen.MOD_EDITOR -> {
795|796|799|                                            val mx = strata_get_mouse_x(window)
796|797|800|                                            val my = strata_get_mouse_y(window)
797|798|802|                                                EditorAction.SAVE -> {
798|799|804|                                                    println("Editor: File saved")
799|800|805|                                                }
800|801|806|                                                EditorAction.FORMAT -> {
801|802|808|                                                    println("Editor: Code formatted")
802|803|809|                                                }
803|804|810|                                                EditorAction.RUN -> {
804|805|811|                                                    println("Editor: Run mod (not implemented)")
805|806|812|                                                }
806|807|813|                                                EditorAction.BACK -> {
807|808|815|                                                    ScreenManager.switchTo(Screen.NATIVE_MODS)
808|809|816|                                                }
809|810|817|                                                EditorAction.SETTINGS -> {
810|811|818|                                                    println("Editor: Settings (not implemented)")
811|812|819|                                                }
812|813|820|                                                null -> {}
813|814|821|                                            }
814|815|822|                                        }
815|816|823|
816|817|824|                                        Screen.INVENTORY -> {
817|818|825|                                            // Clicking a hotbar slot selects it; clicking a
818|819|826|                                            // storage slot swaps it with the selected slot.
819|820|827|                                            val mx = strata_get_mouse_x(window)
820|821|828|                                            val my = strata_get_mouse_y(window)
821|822|829|                                            InventoryScreen.hotbarIndexAt(mx, my, windowWidth, windowHeight)
822|823|830|                                                ?.let { selectedSlot = it }
823|824|831|                                            val inv = inventory
824|825|832|                                            InventoryScreen.storageIndexAt(mx, my, windowWidth, windowHeight)
825|826|833|                                                ?.let { idx -> if (inv != null) inv.swap(idx, selectedSlot) }
826|827|834|                                        }
827|828|835|
828|829|836|                                        Screen.GAME -> {
829|830|837|                                            // Spectators pass through the world without
830|831|838|                                            // touching it; chat typing swallows the mouse.
831|832|839|                                            if (!chatOpen && gameMode != GameMode.SPECTATOR) {
832|833|840|                                                player?.let { p ->
833|834|841|                                                    val cy = cos(camera.yaw); val sy = sin(camera.yaw)
834|835|842|                                                    val cp = cos(camera.pitch); val sp = sin(camera.pitch)
835|836|843|                                                    p.forward = Vec3(sy * cp, -sp, cy * cp)
836|837|844|                                                    val hit = p.removeTarget()
837|838|845|                                                    if (hit != null) {
838|839|846|                                                        streamer?.onBlockEdited(hit.block.x, hit.block.y, hit.block.z)
839|840|847|                                                        // Breaking blocks earns XP (survival only —
840|841|848|                                                        // creative/spectator don't level up).
841|842|849|                                                        if (gameMode == GameMode.SURVIVAL) {
842|843|850|                                                            val ups = survival.addXp(1f)
843|844|851|                                                            if (ups > 0) println("Level up! Now level ${survival.level}")
844|845|852|                                                        }
845|846|853|                                                        println("Block removed at ${hit.block}")
846|847|854|                                                    }
847|848|855|                                                }
848|849|856|                                            }
849|850|857|                                        }
850|851|858|                                    }
851|852|859|                                }
852|853|860|                                SDL_MOUSE_BUTTON_RIGHT.toInt() -> {
853|854|861|                                    // Eating/drinking is survival-only; placing works in
854|855|862|                                    // survival + creative; spectator can't interact at all.
855|856|863|                                    if (ScreenManager.current == Screen.GAME && !chatOpen && gameMode != GameMode.SPECTATOR) {
856|857|864|                                        player?.let { p ->
857|858|865|                                            val cy = cos(camera.yaw); val sy = sin(camera.yaw)
858|859|866|                                            val cp = cos(camera.pitch); val sp = sin(camera.pitch)
859|860|867|                                            p.forward = Vec3(sy * cp, -sp, cy * cp)
860|861|868|                                            // Use the LIVE hotbar slot — it can be empty now.
861|862|869|                                            val item = inventory?.hotbar?.getOrNull(selectedSlot)
862|863|870|                                            if (item != null) {
863|864|871|                                                when {
864|865|872|                                                    item.food > 0 && gameMode == GameMode.SURVIVAL -> {
865|866|873|                                                        // Eat the food slot: restores hunger (bars animate back up)
866|867|874|                                                        survival.eat(item.food.toFloat())
867|868|875|                                                        println("Ate food (+${item.food} hunger, now ${survival.hunger.roundToInt()})")
868|869|876|                                                    }
869|870|877|                                                    item.drink > 0 && gameMode == GameMode.SURVIVAL -> {
870|871|878|                                                        // Drink the water bottle: restores thirst
871|872|879|                                                        survival.drink(item.drink.toFloat())
872|873|880|                                                        println("Drank water (+${item.drink} thirst, now ${survival.thirst.roundToInt()})")
873|874|881|                                                    }
874|875|882|                                                    item.block != null -> {
875|876|883|                                                        val hit = p.placeTarget(item.block)
876|877|884|                                                        if (hit != null) {
877|878|885|                                                            streamer?.onBlockEdited(hit.adjacent.x, hit.adjacent.y, hit.adjacent.z)
878|879|886|                                                            println("Placed ${item.block} at ${hit.adjacent}")
879|880|887|                                                        }
880|881|888|                                                    }
881|882|889|                                                    else -> {}
882|883|890|                                                }
883|884|891|                                            }
884|885|892|                                        }
885|886|893|                                    }
886|887|894|                                }
887|888|895|                            }
888|889|896|                        }
889|890|897|                    }
890|891|898|                }
891|892|899|            }
892|893|900|
893|894|901|            /* ── Game-mode logic only (streaming, movement, survival) ── */
894|895|902|            if (ScreenManager.current == Screen.GAME) {
895|896|903|                val s = streamer
896|897|904|                val p = player
897|898|905|                val w = world
898|899|906|                if (s != null && p != null && w != null) {
899|900|907|                    /* Stream chunks around the player; upload mesh only when dirty */
900|901|908|                    val pcx = w.worldToChunkX(floor(camera.x).toInt())
901|902|909|                    val pcy = w.worldToChunkY(floor(camera.y).toInt())
902|903|910|                    val pcz = w.worldToChunkZ(floor(camera.z).toInt())
903|904|911|                    s.update(pcx, pcy, pcz)
904|905|912|
905|906|913|                    if (s.dirty && s.meshKeys().isNotEmpty()) {
906|907|914|                        requestMeshRebuild()
907|908|915|                    }
908|909|916|
909|910|917|                    /* Take the finished background mesh and upload it to the GPU. */
910|911|918|                    pendingMesh?.let { d ->
911|912|919|                        if (d.isCompleted) {
912|913|920|                            val built = d.getCompleted()
913|914|921|                            pendingMesh = null
914|915|922|                            currentData = built.solid
915|916|923|                            currentVertexCount = currentData.size / 7
916|917|924|                            currentWaterData = built.water
917|918|925|                            currentWaterVertexCount = currentWaterData.size / 7
918|919|926|                            currentRanges = built.ranges
919|920|927|                            uploadVertexBuffer()
920|921|928|                            uploadWaterBuffer()
921|922|929|                            println("World mesh rebuilt: ${currentVertexCount} solid + ${currentWaterVertexCount} water verts across ${s.meshKeys().size} chunks (${currentRanges.size} ranges)")
922|923|930|                            // Rebuild only if genuinely new meshes landed while the
923|924|931|                            // build was running (epoch moved). Otherwise clear the flag
924|925|932|                            // so the whole-world rebuild storm stops at steady state.
925|926|933|                            if (s.meshEpoch() > pendingEpoch) {
926|927|934|                                requestMeshRebuild()
927|928|935|                            } else {
928|929|936|                                s.markMeshUploaded()
929|930|937|                            }
930|931|938|                        }
931|932|939|                    }
932|933|940|
933|934|941|                    /* ── Fixed 60 Hz simulation steps ──
934|935|942|                     * Movement + survival advance at a constant rate; the
935|936|943|                     * accumulator guarantees exactly-once-per-FIXED_DT even if
936|937|944|                     * frames are uneven. Streaming stays per-frame above. */
937|938|945|                    fun held(scancode: Int): Boolean =
938|939|946|                        strata_key_down(scancode) || (scancode in keyState.indices && keyState[scancode])
939|940|947|
940|941|948|                    while (accumulator >= FIXED_DT) {
941|942|949|                        accumulator -= FIXED_DT
942|943|950|
943|944|951|                        /* WASD + yaw -> world-space movement direction. */
944|945|952|                        val cy = cos(camera.yaw); val sy = sin(camera.yaw)
945|946|953|                        var dx = 0f; var dz = 0f
946|947|954|                        if (held(SDL_SCANCODE_W)) { dx += sy; dz += cy }
947|948|955|                        if (held(SDL_SCANCODE_S)) { dx -= sy; dz -= cy }
948|949|956|                        if (held(SDL_SCANCODE_A)) { dx += cy; dz -= sy }
949|950|957|                        if (held(SDL_SCANCODE_D)) { dx -= cy; dz += sy }
950|951|958|
951|952|959|                        /* Normalize horizontal movement so diagonal isn't faster */
952|953|960|                        val hLen = sqrt(dx * dx + dz * dz)
953|954|961|                        if (hLen > 0f) { dx /= hLen; dz /= hLen }
954|955|962|
955|956|963|                        /* ── Player physics (fixed step), mode-aware: ──
956|957|964|                         *  survival   — gravity, AABB voxel collision, jump/sneak/swim
957|958|965|                         *  creative   — flight (no gravity), still blocked by walls
958|959|966|                         *  spectator  — no-clip flight straight through the world
959|960|967|                         * The camera is positioned at the player's eye after. ── */
960|961|968|                        p.tick(
961|962|969|                            FIXED_DT, dx, dz,
962|963|970|                            jump = held(SDL_SCANCODE_SPACE),
963|964|971|                            sneak = held(SDL_SCANCODE_LSHIFT),
964|965|972|                            mode = gameMode
965|966|973|                        )
966|967|974|                        camera = camera.copy(
967|968|975|                            x = p.position.x,
968|969|976|                            y = p.position.y + Player.EYE_HEIGHT,
969|970|977|                            z = p.position.z
970|971|978|                        )
971|972|979|
972|973|980|                        /* ── Survival sim (fixed step) — survival mode only:
973|974|981|                         * hunger + thirst drain, regen requires BOTH well-fed
974|975|982|                         * and well-hydrated, drowning underwater, starvation +
975|976|983|                         * dehydration at empty bars. Health is double hearts
976|977|984|                         * (max 40) and bottoms out at 1 (gentle sim, no
977|978|985|                         * death). Creative/spectator are immortal. */
978|979|986|                        if (gameMode == GameMode.SURVIVAL) survival.tick(FIXED_DT, p.inWater)
979|980|987|                    }
980|981|988|
981|982|989|                    /* ── Ease bar fill toward targets; ghost lags behind.
982|983|990|                     * This runs per-frame (not per fixed step) so the HUD
983|984|991|                     * animation stays smooth at any frame rate. ── */
984|985|992|                    val ease = (frameTime * 6f).coerceAtMost(1f)
985|986|993|                    val ghostEase = (frameTime * 2f).coerceAtMost(1f)
986|987|994|                    healthDisplay += (survival.health - healthDisplay) * ease
987|988|995|                    hungerDisplay += (survival.hunger - hungerDisplay) * ease
988|989|996|                    thirstDisplay += (survival.thirst - thirstDisplay) * ease
989|990|997|                    healthGhost += (survival.health - healthGhost) * ghostEase
990|991|998|                    hungerGhost += (survival.hunger - hungerGhost) * ghostEase
991|992|999|                    thirstGhost += (survival.thirst - thirstGhost) * ghostEase
992|993|1000|                }
993|994|1001|            }
994|995|1002|
995|996|1003|            /* ── Build + upload the UI overlay (title / inventory / HUD) ── */
996|997|1004|            if (ScreenManager.current == Screen.TITLE) {
997|998|1005|                val titleState = TitleScreen.TitleState(
998|999|1006|                    time = strata_get_ticks().toFloat() / 1000f,
999|1000|1007|                    mouseX = strata_get_mouse_x(window),
1000|1001|1008|                    mouseY = strata_get_mouse_y(window)
1001|1002|1009|                )
1002|1003|1010|                val uiData = TitleScreen.build(titleState, windowWidth, windowHeight)
1003|1004|1011|                if (uiData.isNotEmpty()) {
1004|1005|1012|                    uiData.usePinned { pinned ->
1005|1006|1013|                        strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (uiData.size / 7).toUInt())
1006|1007|1014|                    }
1007|1008|1015|                }                } else if (ScreenManager.current == Screen.NATIVE_MODS) {
1008|1009|1016|                    // Use ImGui for Native Mods screen
1009|1010|1017|                    if (!strataImGuiInitialized) {
1010|1012|1019|                            instance, selected, device, queueIndex, queue, surface, window, 2u
1011|1013|1020|                        )
1012|1014|1021|                    }
1013|1015|1022|                    
1014|1016|1023|                    if (strataImGuiInitialized) {
1015|1018|1025|                        
1016|1020|1027|                        
1017|1021|1028|                        if (action == 1) {
1018|1022|1029|                            // EDITOR clicked
1019|1023|1030|                            ScreenManager.switchTo(Screen.MOD_EDITOR)
1020|1024|1031|                        } else if (action == 2) {
1021|1025|1032|                            // MODS clicked
1022|1026|1033|                            ScreenManager.switchTo(Screen.MOD_LIST)
1023|1027|1034|                        } else if (action == 3) {
1024|1028|1035|                            // BACK clicked
1025|1029|1036|                            ScreenManager.switchTo(Screen.TITLE)
1026|1030|1037|                        }
1027|1031|1038|                        
1028|1032|1039|                        // Prepare ImGui draw data for rendering inside the Vulkan render pass.
1029|1034|1041|                    }
1030|1035|1042|                } else if (ScreenManager.current == Screen.MOD_LIST) {
1031|1036|1043|                    val modListState = ModListScreen.ModListState(
1032|1037|1044|                        time = strata_get_ticks().toFloat() / 1000f,
1033|1038|1045|                        mouseX = strata_get_mouse_x(window),
1034|1039|1046|                        mouseY = strata_get_mouse_y(window),
1035|1040|1047|                        modNames = modRegistry.names,
1036|1041|1048|                        modEnabled = modRegistry.enabled,
1037|1042|1049|                        scrollOffset = modListScroll
1038|1043|1050|                    )
1039|1044|1051|                    val listData = ModListScreen.build(modListState, windowWidth, windowHeight)
1040|1045|1052|                    if (listData.isNotEmpty()) {
1041|1046|1053|                        listData.usePinned { pinned ->
1042|1047|1054|                            strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (listData.size / 7).toUInt())
1043|1048|1055|                        }
1044|1049|1056|                    }
1045|1050|1087|                } else if (ScreenManager.current == Screen.INVENTORY) {
1046|1051|1088|                val inv = inventory
1047|1052|1089|                if (inv != null) {
1048|1053|1090|                    val invData = InventoryScreen.build(
1049|1054|1091|                        InventoryScreen.InventoryState(
1050|1055|1092|                            storage = inv.storage,
1051|1056|1093|                            hotbar = inv.hotbar,
1052|1057|1094|                            selectedSlot = selectedSlot,
1053|1058|1095|                            mouseX = strata_get_mouse_x(window),
1054|1059|1096|                            mouseY = strata_get_mouse_y(window)
1055|1060|1097|                        ),
1056|1061|1098|                        windowWidth, windowHeight
1057|1062|1099|                    )
1058|1063|1100|                    if (invData.isNotEmpty()) {
1059|1064|1101|                        invData.usePinned { pinned ->
1060|1065|1102|                            strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (invData.size / 7).toUInt())
1061|1066|1103|                        }
1062|1067|1104|                    }
1063|1068|1105|                }
1064|1069|1106|            } else {
1065|1070|1107|                var hudData = Hud.build(
1066|1071|1108|                    Hud.HudState(
1067|1072|1109|                        camX = camera.x, camY = camera.y, camZ = camera.z,
1068|1073|1110|                        yaw = camera.yaw,
1069|1074|1111|                        fps = fps,
1070|1075|1112|                        seed = world?.seed ?: 12345,
1071|1076|1113|                        chunks = streamer?.meshKeys()?.size ?: 0,
1072|1077|1114|                        health = survival.health.roundToInt(),
1073|1078|1115|                        hunger = survival.hunger.roundToInt(),
1074|1079|1116|                        thirst = survival.thirst.roundToInt(),
1075|1080|1117|                        healthDisplay = healthDisplay,
1076|1081|1118|                        hungerDisplay = hungerDisplay,
1077|1082|1119|                        thirstDisplay = thirstDisplay,
1078|1083|1120|                        healthGhost = healthGhost,
1079|1084|1121|                        hungerGhost = hungerGhost,
1080|1085|1122|                        thirstGhost = thirstGhost,
1081|1086|1123|                        xp = survival.xpProgress,
1082|1087|1124|                        xpLevel = survival.level,
1083|1088|1125|                        selectedSlot = selectedSlot,
1084|1089|1126|                        time = strata_get_ticks().toFloat() / 1000f,
1085|1090|1127|                        hotbar = inventory?.hotbar ?: Hud.HOTBAR,
1086|1091|1128|                        gamemode = gameMode,
1087|1092|1129|                        gpuName = gpuName,
1088|1093|1130|                        vulkanVersion = gpuVulkanVersion,
1089|1094|1131|                        cpuLine = cpuLine,
1090|1095|1132|                        osLine = osLine
1091|1096|1133|                    ),
1092|1097|1134|                    windowWidth, windowHeight
1093|1098|1135|                )
1094|1099|1136|                /* ── Chat overlay: the full typing box while open, otherwise a
1095|1100|1137|                 * small recent-messages log in the corner. Both merge into the
1096|1101|1138|                 * same HUD buffer. Suggestion selection is keyboard-only
1097|1102|1139|                 * (Tab / Up / Down) — the mouse stays captured while typing,
1098|1103|1140|                 * so the dropdown is not clickable. ── */
1099|1104|1141|                if (chatOpen) {
1100|1105|1142|                    val chatData = ChatOverlay.build(
1101|1106|1143|                        ChatOverlay.ChatState(
1102|1107|1144|                            lines = chat.lines,
1103|1108|1145|                            input = chat.input,
1104|1109|1146|                            suggestions = chatSuggestions,
1105|1110|1147|                            selectedSuggestion = chatSuggestionIndex,
1106|1111|1148|                            cursorVisible = (strata_get_ticks().toInt() / 500) % 2 == 0,
1107|1112|1149|                            open = true
1108|1113|1150|                        ),
1109|1114|1151|                        windowWidth, windowHeight
1110|1115|1152|                    )
1111|1116|1153|                    if (chatData.isNotEmpty()) hudData += chatData
1112|1117|1154|                } else if (chat.lines.isNotEmpty()) {
1113|1118|1155|                    // Recent-messages log — only drawn once something was said,
1114|1119|1156|                    // so an empty chat adds nothing to the HUD.
1115|1120|1157|                    val chatData = ChatOverlay.build(
1116|1121|1158|                        ChatOverlay.ChatState(
1117|1122|1159|                            lines = chat.lines.takeLast(3),
1118|1123|1160|                            input = "",
1119|1124|1161|                            suggestions = emptyList(),
1120|1125|1162|                            selectedSuggestion = 0,
1121|1126|1163|                            cursorVisible = false,
1122|1127|1164|                            open = false
1123|1128|1165|                        ),
1124|1129|1166|                        windowWidth, windowHeight
1125|1130|1167|                    )
1126|1131|1168|                    if (chatData.isNotEmpty()) hudData += chatData
1127|1132|1169|                }
1128|1133|1170|
1129|1134|1171|                if (hudData.isNotEmpty()) {
1130|1135|1172|                    hudData.usePinned { pinned ->
1131|1136|1173|                        strata_create_vertex_buffer(instance.handle, selected.handle, device, uiPipeline, pinned.addressOf(0), (hudData.size / 7).toUInt())
1132|1137|1174|                    }
1133|1138|1175|                }
1134|1139|1176|            }
1135|1140|1177|
1136|1141|1178|            /* World uniforms: MVP(16) + camera pos(3) + time(1) = 80 bytes */
1137|1142|1179|            val aspect = windowWidth.toFloat() / windowHeight.toFloat()
1138|1143|1180|            val mvp = computeMVP(camera, aspect)
1139|1144|1181|            val worldPC = FloatArray(20)
1140|1145|1182|            mvp.copyInto(worldPC, 0)
1141|1146|1183|            worldPC[16] = camera.x
1142|1147|1184|            worldPC[17] = camera.y
1143|1148|1185|            worldPC[18] = camera.z
1144|1149|1186|            worldPC[19] = strata_get_ticks().toFloat() / 1000f
1145|1150|1187|            /* UI draws straight in clip space, so its MVP is identity */
1146|1151|1188|            val hudMvp = Mat4.identity().m
1147|1152|1189|
1148|1153|1190|            /* Frustum culling: keep only chunks whose AABB intersects the
1149|1154|1191|             * camera's view. Each kept chunk becomes 4 uint32s —
1150|1155|1192|             * (solidStart, solidCount, waterStart, waterCount) — that the C
1151|1156|1193|             * renderer draws via vkCmdDraw firstVertex offsets. Off-screen
1152|1157|1194|             * chunks cost zero GPU work. */
1153|1158|1195|            val frustum = Frustum(mvp)
1154|1159|1196|            val visible = currentRanges.filter { r ->
1155|1160|1197|                frustum.intersectsAabb(
1156|1161|1198|                    r.minX, r.minY, r.minZ,
1157|1162|1199|                    r.minX + chunkSize, r.minY + chunkSize.toFloat(), r.minZ + chunkSize
1158|1163|1200|                )
1159|1164|1201|            }
1160|1165|1202|            val drawArray = UIntArray(visible.size * 4)
1161|1166|1203|            var di = 0
1162|1167|1204|            for (r in visible) {
1163|1168|1205|                drawArray[di++] = r.solidStart.toUInt()
1164|1169|1206|                drawArray[di++] = r.solidCount.toUInt()
1165|1170|1207|                drawArray[di++] = r.waterStart.toUInt()
1166|1171|1208|                drawArray[di++] = r.waterCount.toUInt()
1167|1172|1209|            }
1168|1173|1210|
1169|1174|1211|            worldPC.usePinned { wp ->
1170|1175|1212|                hudMvp.usePinned { hp ->
1171|1176|1213|                    if (drawArray.isNotEmpty()) {
1172|1177|1214|                        drawArray.usePinned { dr ->
1173|1178|1215|                            strata_render_frame(instance.handle, selected.handle, device, queue, swapchain, pipeline, wp.addressOf(0), waterPipeline, uiPipeline, hp.addressOf(0), dr.addressOf(0), drawArray.size.toUInt())
1174|1179|1216|                        }
1175|1180|1217|                    } else {
1176|1181|1218|                        strata_render_frame(instance.handle, selected.handle, device, queue, swapchain, pipeline, wp.addressOf(0), waterPipeline, uiPipeline, hp.addressOf(0), null, 0u)
1177|1182|1219|                    }
1178|1183|1220|                }
1179|1184|1221|            }
1180|1185|1222|            // Inline screenshot result (set by render_frame after the readback).
1181|1186|1223|            if (strata_capture_done(swapchain) != 0u) {
1182|1187|1224|                println("Screenshot saved: $pendingShotName")
1183|1188|1225|                strata_capture_ack(swapchain)
1184|1189|1226|            }
1185|1190|1227|            // Intel Xe fix: if the swapchain went out of date (resize, TDR,
1186|1191|1228|            // minimize), rebuild immediately so the next frame has a valid
1187|1192|1229|            // surface. Without this the screen freezes / goes black.
1188|1193|1230|            if (strata_swapchain_needs_rebuild(swapchain) != 0u) {
1189|1194|1231|                memScoped {
1190|1195|1232|                    val pw = alloc<IntVar>()
1191|1196|1233|                    val ph = alloc<IntVar>()
1192|1197|1234|                    strata_get_window_size_pixels(window, pw.ptr, ph.ptr)
1193|1198|1235|                    val w = if (pw.value > 0) pw.value else windowWidth
1194|1199|1236|                    val h = if (ph.value > 0) ph.value else windowHeight
1195|1200|1237|                    if (!rebuildSwapchain(w, h)) {
1196|1201|1238|                        println("Swapchain rebuild failed — exiting game loop")
1197|1202|1239|                        running = false
1198|1203|1240|                    }
1199|1204|1241|                }
1200|1205|1242|            }
1201|1206|1243|
1202|1207|1244|            SDL_Delay(16u) // ~60 FPS cap
1203|1208|1245|        }
1204|1209|1246|
1205|1210|1247|        /* ── ImGui Cleanup ── */
1206|1211|1248|        if (strataImGuiInitialized) {
1207|1213|1250|        }
1208|1214|1251|        
1209|1215|1256|        }
1210|1216|1257|        
1211|1217|1258|        /* ── Main Cleanup ── */
1212|1218|1259|        scope.cancel()
1213|1219|1260|        strata_destroy_pipeline(instance.handle, device, pipeline)
1214|1220|1261|        strata_destroy_pipeline(instance.handle, device, waterPipeline)
1215|1221|1262|        strata_destroy_pipeline(instance.handle, device, uiPipeline)
1216|1222|1263|        strata_destroy_swapchain(instance.handle, device, swapchain)
1217|1223|1264|        strata_destroy_allocator()
1218|1224|1265|        strata_destroy_device(instance.handle, device)
1219|1225|1266|        strata_destroy_vulkan_surface(instance.handle, surface)
1220|1226|1267|        instance.close()
1221|1227|1268|    }
1222|1228|1269|    vulkan.close()
1223|1229|1270|
1224|1230|1271|    SDL_DestroyWindow(window)
1225|1231|1272|    SDL_Quit()
1226|1232|1273|}
1227|1233|1274|