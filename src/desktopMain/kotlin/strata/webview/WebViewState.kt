package strata.webview

import kotlin.math.sin
import kotlin.math.PI

/**
 * State management for the webview-based mod editor.
 * 
 * This class manages the lifecycle and state of the webview
 * used for the Monaco Editor integration.
 */
class WebViewState {
    
    /** Current webview instance (null if not created). */
    var webview: WebView? = null
        private set
    
    /** Whether the webview is initialized and ready. */
    val isReady: Boolean
        get() = webview?.isValid() == true
    
    /** Whether the webview is currently visible. */
    var isVisible: Boolean = false
        private set
    
    /** Current file being edited. */
    var currentFile: String = "mod.js"
    
    /** File contents cache. */
    val files: MutableMap<String, String> = mutableMapOf(
        "mod.js" to getDefaultModJs(),
        "mod.json" to getDefaultModJson()
    )
    
    /** Editor theme. */
    var theme: String = "dark"
    
    /** Callback for when a file is saved. */
    var onSave: ((filename: String, content: String) -> Unit)? = null
    
    /**
     * Create or initialize the webview.
     * 
     * @param width Window width
     * @param height Window height
     * @param debug Enable developer tools
     * @return true if successful, false otherwise
     */
    fun create(width: Int = 1280, height: Int = 720, debug: Boolean = false): Boolean {
        if (webview?.isValid() == true) {
            println("[WebViewState] Webview already exists")
            return true
        }
        
        println("[WebViewState] Creating webview (${width}x${height}, debug=$debug)...")
        val wv = WebView.create("Strata Mod Editor", width, height, debug)
        
        if (wv == null) {
            println("[WebViewState] Failed to create webview")
            return false
        }
        
        webview = wv
        println("[WebViewState] Webview created: ${WebView.version()}")
        
        // Load Monaco Editor HTML
        loadMonacoEditor()
        
        // Register Kotlin bridge functions
        registerBridge()
        
        return true
    }
    
    /**
     * Show the webview window.
     */
    fun show() {
        webview?.let { wv ->
            if (wv.isValid()) {
                wv.show()
                isVisible = true
                println("[WebViewState] Webview shown")
            }
        }
    }
    
    /**
     * Hide the webview window.
     */
    fun hide() {
        webview?.let { wv ->
            if (wv.isValid()) {
                wv.hide()
                isVisible = false
                println("[WebViewState] Webview hidden")
            }
        }
    }
    
    /**
     * Destroy the webview and free resources.
     */
    fun destroy() {
        webview?.let { wv ->
            if (wv.isValid()) {
                wv.terminate()
                wv.destroy()
            }
        }
        webview = null
        isVisible = false
        println("[WebViewState] Webview destroyed")
    }
    
    /**
     * Load the Monaco Editor HTML content.
     */
    private fun loadMonacoEditor() {
        val wv = webview ?: return
        
        // Try to load from resources first
        val htmlContent = loadHtmlFromResources()
        if (htmlContent != null) {
            wv.setHtml(htmlContent)
            println("[WebViewState] Loaded Monaco Editor from resources")
        } else {
            // Fallback: load from URL (requires internet)
            println("[WebViewState] Resources not found, using embedded HTML")
            wv.setHtml(getEmbeddedMonacoHtml())
        }
    }
    
    /**
     * Load HTML from desktop resources.
     */
    private fun loadHtmlFromResources(): String? {
        // In a real implementation, this would load from the resources directory
        // For now, return null to use embedded HTML
        return null
    }
    
    /**
     * Register bridge functions for Kotlin-JS communication.
     */
    private fun registerBridge() {
        val wv = webview ?: return
        
        // The webview will call these functions via window.strataXxx
        // We handle them by executing JavaScript in the webview
        
        println("[WebViewState] Bridge registered")
    }
    
    /**
     * Set the current file in the editor.
     * 
     * @param filename File to switch to
     */
    fun setCurrentFile(filename: String) {
        currentFile = filename
        webview?.execute("switchFile('$filename')")
    }
    
    /**
     * Get the content of the current file from the editor.
     * 
     * @return File content, or null if not available
     */
    fun getCurrentContent(): String? {
        val wv = webview ?: return null
        
        // Execute JavaScript to get the content
        val js = """
            (function() {
                if (window.strataGetEditorContent) {
                    const files = JSON.parse(window.strataGetEditorContent());
                    return files['$currentFile']?.content || '';
                }
                return '';
            })()
        """.trimIndent()
        
        return wv.evalString(js)
    }
    
    /**
     * Set the content of a file in the editor.
     * 
     * @param filename File to update
     * @param content New content
     */
    fun setFileContent(filename: String, content: String) {
        files[filename] = content
        webview?.execute("strataSetEditorContent('$filename', ${content.escapeJs()})")
    }
    
    /**
     * Save the current file.
     */
    fun saveCurrentFile() {
        val content = getCurrentContent()
        if (content != null) {
            files[currentFile] = content
            onSave?.invoke(currentFile, content)
            println("[WebViewState] Saved $currentFile")
        }
    }
    
    /**
     * Format the code in the editor.
     */
    fun formatCode() {
        webview?.execute("formatCode()")
    }
    
    /**
     * Toggle the minimap.
     */
    fun toggleMinimap() {
        webview?.execute("toggleMinimap()")
    }
    
    /**
     * Toggle word wrap.
     */
    fun toggleWordWrap() {
        webview?.execute("toggleWordWrap()")
    }
    
    /**
     * Set the editor theme.
     * 
     * @param newTheme Theme name: "dark" or "light"
     */
    fun setTheme(newTheme: String) {
        theme = newTheme
        webview?.execute("editor.updateOptions({ theme: '$newTheme' })")
    }
    
    /**
     * Process webview events (non-blocking).
     * Call this periodically from the main loop.
     */
    fun processEvents() {
        webview?.let { wv ->
            if (wv.isValid() && isVisible) {
                // Non-blocking event processing
                wv.run(blocking = false)
            }
        }
    }
    
    /**
     * Get all file contents as a map.
     * 
     * @return Map of filename to content
     */
    fun getAllFiles(): Map<String, String> {
        return files.toMap()
    }
    
    /**
     * Load files from a mod directory.
     * 
     * @param modPath Path to the mod directory
     * @param fileContents Map of filename to content
     */
    fun loadModFiles(modPath: String, fileContents: Map<String, String>) {
        files.clear()
        files.putAll(fileContents)
        
        // Load files into editor
        for ((filename, content) in fileContents) {
            setFileContent(filename, content)
        }
        
        // Switch to mod.js if available
        if (fileContents.containsKey("mod.js")) {
            setCurrentFile("mod.js")
        }
    }
    
    // ── Helper Functions ──
    
    private fun String.escapeJs(): String {
        return "\"${this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
    
    private fun getDefaultModJs(): String {
        return """
/**
 * Strata Mod Template
 */
const MOD_NAME = "My Mod";

function onModLoad() {
    console.log(`[${'$'}{MOD_NAME}] Loaded!`);
}

function onModUnload() {
    console.log(`[${'$'}{MOD_NAME}] Unloaded.`);
}

module.exports = { onModLoad, onModUnload };
""".trimIndent()
    }
    
    private fun getDefaultModJson(): String {
        return """
{
    "name": "my_mod",
    "displayName": "My Mod",
    "version": "1.0.0",
    "main": "mod.js"
}
""".trimIndent()
    }
    
    /**
     * Get embedded Monaco Editor HTML for fallback.
     * This is a simplified version for when resources aren't available.
     */
    private fun getEmbeddedMonacoHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Strata Mod Editor</title>
    <style>
        body { margin: 0; background: #1e1e1e; color: #d4d4d4; font-family: monospace; }
        #editor { width: 100vw; height: 100vh; }
    </style>
</head>
<body>
    <div id="editor"></div>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.min.js"></script>
    <script>
        require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' } });
        require(['vs/editor/editor.main'], function() {
            monaco.editor.create(document.getElementById('editor'), {
                value: '// Strata Mod Editor\n// Loading...',
                language: 'javascript',
                theme: 'vs-dark'
            });
        });
    </script>
</body>
</html>
""".trimIndent()
    }
}
