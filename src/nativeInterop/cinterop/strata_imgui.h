#ifndef STRATA_IMGUI_H
#define STRATA_IMGUI_H

/*
 * strata_imgui.h — Pure C wrapper for Dear ImGui (SDL3 + Vulkan).
 * 
 * This header declares only the functions we need from ImGui,
 * avoiding the complexity of cimgui.h which has issues with
 * Kotlin/Native cinterop.
 * 
 * The implementation is in strata_imgui_impl.cpp which includes
 * the full imgui headers and links against cimgui.
 */

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque handle to the ImGui state (stored in C++ as a static). */
typedef void* StrataImGuiCtx;

/* ── Lifecycle ── */

/**
 * Initialize Dear ImGui + SDL3 + Vulkan backends.
 * Must be called ONCE after Vulkan instance/device/queue are created
 * and BEFORE the first frame.
 *
 * Returns 0 on success, negative on error.
 */
int strata_imgui_init(
    void* instance,      /* VkInstance */
    void* physicalDevice, /* VkPhysicalDevice */
    void* device,        /* VkDevice */
    uint32_t queueFamily,
    void* queue,         /* VkQueue */
    void* surface,       /* VkSurfaceKHR */
    void* window,        /* SDL_Window* */
    uint32_t minImageCount
);

/** Shut down ImGui and free all resources. */
void strata_imgui_shutdown(void);

/* ── Frame lifecycle ── */

/** Begin a new ImGui frame. Call once per frame before any widget calls. */
void strata_imgui_new_frame(void);

/** Render the ImGui draw data. Call once per frame after all widget calls. */
void strata_imgui_render(void);

/* ── Event processing ── */

/**
 * Forward an SDL event to ImGui.
 * Returns 1 if ImGui consumed the event, 0 otherwise.
 */
int strata_imgui_process_event(void* sdlEvent);

/** Returns 1 if ImGui wants mouse capture. */
int strata_imgui_want_capture_mouse(void);

/** Returns 1 if ImGui wants keyboard capture. */
int strata_imgui_want_capture_keyboard(void);

/* ── Native Mods Screen ── */

/**
 * Draw the Native Mods screen with two main buttons:
 *   EDITOR  — opens the mod code editor
 *   MODS    — shows loaded/unloaded mods list
 *
 * Returns:
 *   0 = no action
 *   1 = user clicked EDITOR
 *   2 = user clicked MODS
 *   3 = user clicked BACK
 */
int strata_imgui_native_mods_screen(void);

/* ── Render Pass Integration ── */

/**
 * Prepare ImGui for rendering. Call after all ImGui widget calls.
 * This finalizes the draw data but does NOT render it yet.
 * Rendering happens inside strata_render_frame via the render pass.
 */
void strata_imgui_prepare_render(void);

/**
 * Render ImGui draw data into the given Vulkan command buffer.
 * MUST be called inside an active render pass (between
 * vkCmdBeginRenderPass and vkCmdEndRenderPass).
 *
 * @param commandBuffer  VkCommandBuffer to record into (as void*)
 */
void strata_imgui_render_to(void* commandBuffer);

/**
 * Returns 1 if ImGui has pending draw data to render.
 */
int strata_imgui_has_draw_data(void);

/* ── Utility ── */

/** Returns 1 if ImGui initialized successfully, 0 otherwise. */
int strata_imgui_is_initialized(void);

#ifdef __cplusplus
}
#endif

#endif /* STRATA_IMGUI_H */
