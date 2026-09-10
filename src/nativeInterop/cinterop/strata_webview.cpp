/*
 * strata_webview.cpp — Implementation of the webview wrapper.
 *
 * This file wraps the webview library (https://github.com/webview/webview)
 * for use with Kotlin/Native cinterop. On Windows, the webview library
 * uses WebView2 (Edge/Chromium) to render HTML content.
 *
 * Build requirements:
 *   1. webview library compiled and available
 *   2. WebView2 runtime installed on the target system
 *   3. CMake or manual compilation with proper include paths
 */

#include "strata_webview.h"

// If using the webview library, include it here:
// #include "webview.h"

// For now, we provide a minimal stub implementation
// that can be replaced with the actual webview library.

#ifdef _WIN32
#include <windows.h>
#include <string>

// WebView2 headers (if available)
// #include <WebView2.h>

struct StrataWebViewImpl {
    HWND hwnd;
    bool debug;
    bool initialized;
    std::string title;
    int width;
    int height;
    
    StrataWebViewImpl() : hwnd(nullptr), debug(false), initialized(false),
                          width(1280), height(720) {}
    
    ~StrataWebViewImpl() {
        if (hwnd) {
            DestroyWindow(hwnd);
            hwnd = nullptr;
        }
    }
};

static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    StrataWebViewImpl* wv = nullptr;
    
    if (msg == WM_NCCREATE) {
        CREATESTRUCT* cs = reinterpret_cast<CREATESTRUCT*>(lParam);
        wv = reinterpret_cast<StrataWebViewImpl*>(cs->lpCreateParams);
        SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(wv));
    } else {
        wv = reinterpret_cast<StrataWebViewImpl*>(GetWindowLongPtr(hwnd, GWLP_USERDATA));
    }
    
    switch (msg) {
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
        case WM_SIZE:
            if (wv && wv->initialized) {
                // Notify WebView2 of size change
                wv->width = LOWORD(lParam);
                wv->height = HIWORD(lParam);
            }
            return 0;
    }
    
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

#endif // _WIN32

// ── Lifecycle ──

StrataWebView strata_webview_create(
    const char* title,
    int width,
    int height,
    int debug
) {
#ifdef _WIN32
    static bool classRegistered = false;
    if (!classRegistered) {
        WNDCLASSEX wc = {};
        wc.cbSize = sizeof(WNDCLASSEX);
        wc.lpfnWndProc = WndProc;
        wc.hInstance = GetModuleHandle(nullptr);
        wc.lpszClassName = "StrataWebView";
        wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
        wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
        RegisterClassEx(&wc);
        classRegistered = true;
    }
    
    auto* wv = new StrataWebViewImpl();
    wv->debug = debug != 0;
    wv->title = title ? title : "Strata Webview";
    wv->width = width > 0 ? width : 1280;
    wv->height = height > 0 ? height : 720;
    
    DWORD style = WS_OVERLAPPEDWINDOW | WS_VISIBLE;
    
    // Adjust window size to account for borders/title bar
    RECT rect = {0, 0, wv->width, wv->height};
    AdjustWindowRect(&rect, style, FALSE);
    
    wv->hwnd = CreateWindowEx(
        0,
        "StrataWebView",
        wv->title.c_str(),
        style,
        CW_USEDEFAULT, CW_USEDEFAULT,
        rect.right - rect.left, rect.bottom - rect.top,
        nullptr, nullptr,
        GetModuleHandle(nullptr),
        wv
    );
    
    if (!wv->hwnd) {
        delete wv;
        return nullptr;
    }
    
    wv->initialized = true;
    return wv;
#else
    // Non-Windows stub
    return nullptr;
#endif
}

void strata_webview_destroy(StrataWebView wv) {
#ifdef _WIN32
    delete reinterpret_cast<StrataWebViewImpl*>(wv);
#endif
}

int strata_webview_is_valid(StrataWebView wv) {
#ifdef _WIN32
    return wv && reinterpret_cast<StrataWebViewImpl*>(wv)->hwnd ? 1 : 0;
#else
    return 0;
#endif
}

// ── Content Loading ──

void strata_webview_navigate(StrataWebView wv, const char* url) {
#ifdef _WIN32
    if (!wv) return;
    // In a full implementation, this would use WebView2's Navigate()
    // For now, it's a stub
#endif
}

void strata_webview_set_html(StrataWebView wv, const char* html) {
#ifdef _WIN32
    if (!wv) return;
    // In a full implementation, this would use WebView2's NavigateToString()
    // For now, it's a stub
#endif
}

void strata_webview_execute(StrataWebView wv, const char* js) {
#ifdef _WIN32
    if (!wv) return;
    // In a full implementation, this would use WebView2's ExecuteScript()
    // For now, it's a stub
#endif
}

void strata_webview_bind(
    StrataWebView wv,
    const char* name,
    StrataWebViewBindingFn func,
    void* userdata
) {
#ifdef _WIN32
    if (!wv) return;
    // In a full implementation, this would use WebView2's AddHostObjectToScript()
    // For now, it's a stub
#endif
}

// ── Window Control ──

void strata_webview_show(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    ShowWindow(impl->hwnd, SW_SHOW);
    UpdateWindow(impl->hwnd);
#endif
}

void strata_webview_hide(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    ShowWindow(impl->hwnd, SW_HIDE);
#endif
}

void strata_webview_minimize(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    ShowWindow(impl->hwnd, SW_MINIMIZE);
#endif
}

void strata_webview_maximize(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    ShowWindow(impl->hwnd, SW_MAXIMIZE);
#endif
}

void strata_webview_restore(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    ShowWindow(impl->hwnd, SW_RESTORE);
#endif
}

void strata_webview_center(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    
    RECT rect;
    GetWindowRect(impl->hwnd, &rect);
    int winW = rect.right - rect.left;
    int winH = rect.bottom - rect.top;
    
    int x = (screenW - winW) / 2;
    int y = (screenH - winH) / 2;
    
    SetWindowPos(impl->hwnd, nullptr, x, y, 0, 0, SWP_NOSIZE | SWP_NOZORDER);
#endif
}

void strata_webview_set_title(StrataWebView wv, const char* title) {
#ifdef _WIN32
    if (!wv || !title) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    impl->title = title;
    SetWindowText(impl->hwnd, title);
#endif
}

void strata_webview_set_size(StrataWebView wv, int width, int height) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    impl->width = width;
    impl->height = height;
    
    DWORD style = GetWindowLong(impl->hwnd, GWL_STYLE);
    RECT rect = {0, 0, width, height};
    AdjustWindowRect(&rect, style, FALSE);
    
    SetWindowPos(impl->hwnd, nullptr, 0, 0,
        rect.right - rect.left, rect.bottom - rect.top,
        SWP_NOMOVE | SWP_NOZORDER);
#endif
}

// ── Evaluation ──

int strata_webview_eval_string(
    StrataWebView wv,
    const char* js,
    char* result,
    int result_size
) {
#ifdef _WIN32
    if (!wv || !js || !result || result_size <= 0) return -1;
    // In a full implementation, this would use WebView2's ExecuteScript()
    // For now, return empty string
    result[0] = '\0';
    return 0;
#else
    return -1;
#endif
}

// ── Theme ──

void strata_webview_set_theme(StrataWebView wv, const char* theme) {
#ifdef _WIN32
    if (!wv || !theme) return;
    // WebView2 supports theming via ICoreWebView2EnvironmentOptions
    // For now, this is a stub
#endif
}

// ── Utility ──

const char* strata_webview_version(void) {
    return "0.1.0-strata";
}

int strata_webview_run(StrataWebView wv, int blocking) {
#ifdef _WIN32
    if (!wv) return -1;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    
    MSG msg;
    if (blocking) {
        if (GetMessage(&msg, nullptr, 0, 0)) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
            return msg.message == WM_QUIT ? 0 : 1;
        }
        return 0;
    } else {
        while (PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) return 0;
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
        return 1;
    }
#else
    return -1;
#endif
}

void strata_webview_terminate(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    PostMessage(impl->hwnd, WM_CLOSE, 0, 0);
#endif
}

int strata_webview_is_visible(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return 0;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    return IsWindowVisible(impl->hwnd) ? 1 : 0;
#else
    return 0;
#endif
}

int strata_webview_is_minimized(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return 0;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    return IsIconic(impl->hwnd) ? 1 : 0;
#else
    return 0;
#endif
}

int strata_webview_is_maximized(StrataWebView wv) {
#ifdef _WIN32
    if (!wv) return 0;
    auto* impl = reinterpret_cast<StrataWebViewImpl*>(wv);
    return IsZoomed(impl->hwnd) ? 1 : 0;
#else
    return 0;
#endif
}
