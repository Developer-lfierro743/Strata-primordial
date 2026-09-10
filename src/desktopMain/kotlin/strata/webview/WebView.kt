package strata.webview

import kotlinx.cinterop.*
import platform.posix.printf

/**
 * Kotlin/Native wrapper for the webview library.
 * 
 * Provides a high-level API for creating and managing webview windows
 * that can display HTML/JS content (e.g., Monaco Editor).
 * 
 * Usage:
 * ```kotlin
 * val webview = WebView.create("Mod Editor", 1280, 720, debug = true)
 * if (webview != null) {
 *     webview.setHtml(monacoEditorHtml)
 *     webview.show()
 *     // ... later
 *     webview.destroy()
 * }
 * ```
 */
@OptIn(ExperimentalForeignApi::class)
class WebView(private val pointer: COpaquePointer) {
    
    companion object {
        /**
         * Create a new webview window.
         * 
         * @param title Window title
         * @param width Window width in pixels (0 for default)
         * @param height Window height in pixels (0 for default)
         * @param debug Enable developer tools
         * @return WebView instance, or null on failure
         */
        fun create(title: String, width: Int = 0, height: Int = 0, debug: Boolean = false): WebView? {
            val ptr = memScoped {
                strata.webview.strata_webview_create(
                    title.cstr.ptr,
                    width,
                    height,
                    if (debug) 1 else 0
                )
            }
            return if (ptr != null) WebView(ptr) else null
        }
        
        /**
         * Get the webview library version.
         */
        fun version(): String {
            return strata.webview.strata_webview_version()?.toKString() ?: "unknown"
        }
    }
    
    /**
     * Check if the webview is valid and ready to use.
     */
    fun isValid(): Boolean {
        return strata.webview.strata_webview_is_valid(pointer) != 0
    }
    
    /**
     * Navigate to a URL.
     * 
     * @param url URL to navigate to (null to reload)
     */
    fun navigate(url: String?) {
        memScoped {
            strata.webview.strata_webview_navigate(pointer, url?.cstr?.ptr)
        }
    }
    
    /**
     * Set the HTML content directly.
     * 
     * @param html HTML content to load
     */
    fun setHtml(html: String) {
        memScoped {
            strata.webview.strata_webview_set_html(pointer, html.cstr.ptr)
        }
    }
    
    /**
     * Execute JavaScript code in the webview.
     * 
     * @param js JavaScript code to execute
     */
    fun execute(js: String) {
        memScoped {
            strata.webview.strata_webview_execute(pointer, js.cstr.ptr)
        }
    }
    
    /**
     * Evaluate JavaScript and get the result as a string.
     * 
     * @param js JavaScript code to evaluate
     * @return Result string, or null on error
     */
    fun evalString(js: String): String? {
        return memScoped {
            val bufferSize = 8192
            val buffer = allocArray<ByteVar>(bufferSize)
            val result = strata.webview.strata_webview_eval_string(pointer, js.cstr.ptr, buffer, bufferSize)
            if (result == 0) {
                buffer.toKString()
            } else {
                null
            }
        }
    }
    
    /**
     * Show the webview window.
     */
    fun show() {
        strata.webview.strata_webview_show(pointer)
    }
    
    /**
     * Hide the webview window.
     */
    fun hide() {
        strata.webview.strata_webview_hide(pointer)
    }
    
    /**
     * Minimize the webview window.
     */
    fun minimize() {
        strata.webview.strata_webview_minimize(pointer)
    }
    
    /**
     * Maximize the webview window.
     */
    fun maximize() {
        strata.webview.strata_webview_maximize(pointer)
    }
    
    /**
     * Restore the webview window.
     */
    fun restore() {
        strata.webview.strata_webview_restore(pointer)
    }
    
    /**
     * Center the webview window on screen.
     */
    fun center() {
        strata.webview.strata_webview_center(pointer)
    }
    
    /**
     * Set the window title.
     * 
     * @param title New title
     */
    fun setTitle(title: String) {
        memScoped {
            strata.webview.strata_webview_set_title(pointer, title.cstr.ptr)
        }
    }
    
    /**
     * Set the window size.
     * 
     * @param width New width in pixels
     * @param height New height in pixels
     */
    fun setSize(width: Int, height: Int) {
        strata.webview.strata_webview_set_size(pointer, width, height)
    }
    
    /**
     * Set the webview theme.
     * 
     * @param theme Theme name: "light", "dark", or "system"
     */
    fun setTheme(theme: String) {
        memScoped {
            strata.webview.strata_webview_set_theme(pointer, theme.cstr.ptr)
        }
    }
    
    /**
     * Check if the window is visible.
     */
    fun isVisible(): Boolean {
        return strata.webview.strata_webview_is_visible(pointer) != 0
    }
    
    /**
     * Check if the window is minimized.
     */
    fun isMinimized(): Boolean {
        return strata.webview.strata_webview_is_minimized(pointer) != 0
    }
    
    /**
     * Check if the window is maximized.
     */
    fun isMaximized(): Boolean {
        return strata.webview.strata_webview_is_maximized(pointer) != 0
    }
    
    /**
     * Run the webview event loop (blocking).
     * 
     * @param blocking true to block until window is closed
     * @return true if the window is still open, false if closed
     */
    fun run(blocking: Boolean = true): Boolean {
        return strata.webview.strata_webview_run(pointer, if (blocking) 1 else 0) != 0
    }
    
    /**
     * Terminate the webview event loop.
     */
    fun terminate() {
        strata.webview.strata_webview_terminate(pointer)
    }
    
    /**
     * Destroy the webview and free all resources.
     */
    fun destroy() {
        strata.webview.strata_webview_destroy(pointer)
    }
    
    // Prevent accidental copying
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = pointer.hashCode()
}
