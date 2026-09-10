package strata.cimgui

import kotlinx.cinterop.COpaquePointer

/**
 * Native ImGui integration for Strata Primordial.
 * Provides Dear ImGui UI with Vulkan backend via cinterop.
 */
expect object StrataImGui {
    /** Initialize ImGui with Vulkan + SDL3 backends. Returns true on success. */
    fun init(
        instance: COpaquePointer,
        physicalDevice: COpaquePointer,
        device: COpaquePointer,
        queueFamily: Int,
        queue: COpaquePointer,
        surface: COpaquePointer,
        window: COpaquePointer,
        minImageCount: ULong
    ): Boolean
    
    /** Shutdown ImGui and free all resources. */
    fun shutdown()
    
    /** Call once per frame before any widget calls. */
    fun newFrame()
    
    /** Process an SDL event. Returns true if ImGui consumed it. */
    fun processEvent(event: COpaquePointer): Boolean
    
    /** Check if ImGui wants mouse capture. */
    fun wantCaptureMouse(): Boolean
    
    /** Check if ImGui wants keyboard capture. */
    fun wantCaptureKeyboard(): Boolean
    
    /**
     * Show the Native Mods screen.
     * @return action code: 0=none, 1=editor, 2=mods list, 3=back
     */
    fun nativeModsScreen(): Int
    
    /** Check if ImGui has been initialized. */
    fun isInitialized(): Boolean
    
    /**
     * Prepare ImGui for rendering. Call after all widget calls.
     * Finalizes draw data but does NOT render — rendering happens
     * inside the Vulkan render pass via strata_render_frame.
     */
    fun prepareRender()
    
    /**
     * Returns true if ImGui has pending draw data to render.
     */
    fun hasDrawData(): Boolean
}
