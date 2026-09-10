#ifndef STRATA_WEBVIEW_H
#define STRATA_WEBVIEW_H

/*
 * strata_webview.h — C wrapper for the webview library.
 * 
 * Provides a lightweight webview for embedding HTML/JS content
 * (e.g., Monaco Editor) in the Strata desktop application.
 * Uses WebView2 on Windows (Edge/Chromium).
 * 
 * The implementation is in strata_webview.cpp which includes
 * the webview library and links against it.
 */

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque handle to the webview instance */
typedef void* StrataWebView;

/* ── Lifecycle ── */

/**
 * Create a new webview window.
 * 
 * @param title Window title
 * @param width Window width in pixels (0 for default)
 * @param height Window height in pixels (0 for default)
 * @param debug Enable developer tools (1) or disable (0)
 * @return Handle to the webview, or NULL on failure
 */
StrataWebView strata_webview_create(
    const char* title,
    int width,
    int height,
    int debug
);

/**
 * Destroy a webview and free all resources.
 * 
 * @param wv Handle to the webview to destroy
 */
void strata_webview_destroy(StrataWebView wv);

/**
 * Check if the webview is valid and ready to use.
 * 
 * @param wv Handle to the webview
 * @return 1 if valid, 0 otherwise
 */
int strata_webview_is_valid(StrataWebView wv);

/* ── Content Loading ── */

/**
 * Navigate the webview to a URL.
 * 
 * @param wv Handle to the webview
 * @param url URL to navigate to (NULL to reload current page)
 */
void strata_webview_navigate(StrataWebView wv, const char* url);

/**
 * Set the HTML content of the webview.
 * 
 * @param wv Handle to the webview
 * @param html HTML content to load (UTF-8)
 */
void strata_webview_set_html(StrataWebView wv, const char* html);

/**
 * Execute JavaScript code in the webview.
 * 
 * @param wv Handle to the webview
 * @param js JavaScript code to execute (UTF-8)
 */
void strata_webview_execute(StrataWebView wv, const char* js);

/**
 * Add a JavaScript function that can be called from the web page.
 * 
 * @param wv Handle to the webview
 * @param name Function name exposed to JavaScript
 * @param func Function to call (receives JSON string argument)
 */
typedef void (*StrataWebViewBindingFn)(const char* arg, void* userdata);

void strata_webview_bind(
    StrataWebView wv,
    const char* name,
    StrataWebViewBindingFn func,
    void* userdata
);

/* ── Window Control ── */

/**
 * Show the webview window.
 * 
 * @param wv Handle to the webview
 */
void strata_webview_show(StrataWebView wv);

/**
 * Hide the webview window.
 * 
 * @param wv Handle to the webview
 */
void strata_webview_hide(StrataWebView wv);

/**
 * Minimize the webview window.
 * 
 * @param wv Handle to the webview
 */
void strata_webview_minimize(StrataWebView wv);

/**
 * Maximize the webview window.
 * 
 * @param wv Handle to the webview
 */
void strata_webview_maximize(StrataWebView wv);

/**
 * Restore the webview window from minimized/maximized state.
 * 
 * @param wv Handle to the webview
 */
void strata_webview_restore(StrataWebView wv);

/**
 * Center the webview window on screen.
 * 
 * @param wv Handle to the webview
 */
void strata_webview_center(StrataWebView wv);

/**
 * Set the title of the webview window.
 * 
 * @param wv Handle to the webview
 * @param title New title (UTF-8)
 */
void strata_webview_set_title(StrataWebView wv, const char* title);

/**
 * Set the window size of the webview.
 * 
 * @param wv Handle to the webview
 * @param width New width in pixels
 * @param height New height in pixels
 */
void strata_webview_set_size(StrataWebView wv, int width, int height);

/* ── Evaluation (synchronous) ── */

/**
 * Evaluate JavaScript and get the result as a string.
 * NOTE: This blocks until the evaluation completes. Use with caution.
 * 
 * @param wv Handle to the webview
 * @param js JavaScript code to evaluate
 * @param result Buffer to receive the result string
 * @param result_size Size of the result buffer
 * @return 0 on success, -1 on error
 */
int strata_webview_eval_string(
    StrataWebView wv,
    const char* js,
    char* result,
    int result_size
);

/* ── Theme ── */

/**
 * Set the webview theme.
 * 
 * @param wv Handle to the webview
 * @param theme Theme name: "light", "dark", or "system"
 */
void strata_webview_set_theme(StrataWebView wv, const char* theme);

/* ── Utility ── */

/**
 * Get the version of the webview library.
 * 
 * @return Version string (do not free)
 */
const char* strata_webview_version(void);

/**
 * Run the webview event loop (blocking).
 * Call this periodically to process webview events.
 * 
 * @param wv Handle to the webview
 * @param blocking 1 to block until window is closed, 0 for non-blocking
 * @return 0 if the window was closed, -1 on error
 */
int strata_webview_run(StrataWebView wv, int blocking);

/**
 * Terminate the webview event loop.
 * 
 * @param wv Handle to the webview
 */
void strata_webview_terminate(StrataWebView wv);

/**
 * Get whether the webview window is visible.
 * 
 * @param wv Handle to the webview
 * @return 1 if visible, 0 if hidden
 */
int strata_webview_is_visible(StrataWebView wv);

/**
 * Get whether the webview window is minimized.
 * 
 * @param wv Handle to the webview
 * @return 1 if minimized, 0 otherwise
 */
int strata_webview_is_minimized(StrataWebView wv);

/**
 * Get whether the webview window is maximized.
 * 
 * @param wv Handle to the webview
 * @return 1 if maximized, 0 otherwise
 */
int strata_webview_is_maximized(StrataWebView wv);

#ifdef __cplusplus
}
#endif

#endif /* STRATA_WEBVIEW_H */
