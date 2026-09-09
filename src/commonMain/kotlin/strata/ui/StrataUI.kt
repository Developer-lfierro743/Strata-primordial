package strata.ui

/**
 * UI abstraction for Strata Primordial.
 * This interface allows switching between different UI backends (ImGui, custom renderer, etc.)
 */
interface StrataUI {
    /** Initialize the UI system */
    fun init()
    
    /** Shutdown the UI system */
    fun shutdown()
    
    /** Start a new frame */
    fun newFrame()
    
    /** Render the current frame */
    fun render()
    
    /** Process an event, returns true if consumed */
    fun processEvent(event: Any): Boolean
    
    /** Check if mouse is captured by UI */
    fun wantCaptureMouse(): Boolean
    
    /** Check if keyboard is captured by UI */
    fun wantCaptureKeyboard(): Boolean
    
    /** Show the Native Mods screen, returns action code */
    fun nativeModsScreen(): Int
    
    /** Check if UI is initialized */
    fun isInitialized(): Boolean
}

/**
 * Simple no-op UI implementation for platforms without ImGui support yet.
 */
class NullUI : StrataUI {
    override fun init() {}
    override fun shutdown() {}
    override fun newFrame() {}
    override fun render() {}
    override fun processEvent(event: Any): Boolean = false
    override fun wantCaptureMouse(): Boolean = false
    override fun wantCaptureKeyboard(): Boolean = false
    override fun nativeModsScreen(): Int = 0
    override fun isInitialized(): Boolean = false
}
