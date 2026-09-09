/*
 * strata_imgui_impl.cpp — Native ImGui implementation for Strata Primordial.
 * 
 * Links Dear ImGui (via cimgui) with SDL3 + Vulkan backends.
 *
 * IMPORTANT: Include cimgui.h for the C wrapper functions (ig* prefix).
 * Do NOT include imgui.h directly — it provides the C++ API.
 */

#define CIMGUI_DEFINE_ENUMS_AND_STRUCTS
#include <cimgui.h>
#include <vulkan/vulkan.h>
#include <SDL3/SDL.h>
#include <backends/imgui_impl_sdl3.h>
#include <backends/imgui_impl_vulkan.h>
#include "strata_imgui.h"
#include <cstdio>
#include <cstring>

// C++ state
static ImGuiContext* g_ctx = nullptr;
static bool g_initialized = false;

// Forward declarations
static void strata_imgui_editor_panel_impl(void);
static int strata_imgui_mods_list_impl(const char** modNames, int* modEnabled, int modCount);

extern "C" {


int strata_imgui_init(
    VkInstance instance,
    VkPhysicalDevice physicalDevice,
    VkDevice device,
    uint32_t queueFamily,
    VkQueue queue,
    VkSurfaceKHR surface,
    void* window,
    uint32_t minImageCount
) {
    if (g_initialized) return 0;
    
    // Create ImGui context
    g_ctx = igCreateContext(nullptr);
    if (!g_ctx) {
        printf("[IMGUI] Failed to create context\n");
        return -1;
    }
    
    // Setup style using cimgui API
    ImGuiStyle* style = igGetStyle();
    style->WindowPadding = {8.0f, 8.0f};
    style->FramePadding = {6.0f, 2.0f};
    style->CellPadding = {4.0f, 2.0f};
    style->ItemSpacing = {8.0f, 4.0f};
    style->ItemInnerSpacing = {4.0f, 4.0f};
    style->TouchExtraPadding = {0.0f, 0.0f};
    style->IndentSpacing = 25.0f;
    style->ColumnsMinSpacing = 6.0f;
    style->ScrollbarSize = 15.0f;
    style->GrabMinSize = 12.0f;
    style->WindowBorderSize = 1.0f;
    style->ChildBorderSize = 1.0f;
    style->PopupBorderSize = 1.0f;
    style->FrameBorderSize = 1.0f;
    style->TabBorderSize = 1.0f;
    style->WindowRounding = 7.0f;
    style->ChildRounding = 4.0f;
    style->FrameRounding = 3.0f;
    style->PopupRounding = 5.0f;
    style->ScrollbarRounding = 9.0f;
    style->GrabRounding = 3.0f;
    style->TabRounding = 4.0f;
    style->WindowTitleAlign = {0.5f, 0.5f};
    
    // Initialize SDL3 backend
    SDL_Window* sdlWindow = (SDL_Window*)window;
    if (!ImGui_ImplSDL3_InitForVulkan(sdlWindow)) {
        printf("[IMGUI] Failed to initialize SDL3 backend\n");
        igDestroyContext(g_ctx);
        g_ctx = nullptr;
        return -2;
    }
    
    // Initialize Vulkan backend
    ImGui_ImplVulkan_InitInfo vulkan_info = {};
    vulkan_info.ApiVersion = VK_API_VERSION_1_2;
    vulkan_info.Instance = instance;
    vulkan_info.PhysicalDevice = physicalDevice;
    vulkan_info.Device = device;
    vulkan_info.QueueFamily = queueFamily;
    vulkan_info.Queue = queue;
    vulkan_info.PipelineCache = VK_NULL_HANDLE;
    vulkan_info.DescriptorPool = VK_NULL_HANDLE;
    vulkan_info.MinImageCount = minImageCount;
    vulkan_info.ImageCount = minImageCount;
    vulkan_info.Allocator = nullptr;
    vulkan_info.CheckVkResultFn = nullptr;
    
    // Set pipeline info (Subpass and MSAASamples)
    vulkan_info.PipelineInfoMain.Subpass = 0;
    vulkan_info.PipelineInfoMain.MSAASamples = VK_SAMPLE_COUNT_1_BIT;
    
    if (!ImGui_ImplVulkan_Init(&vulkan_info)) {
        printf("[IMGUI] Failed to initialize Vulkan backend\n");
        ImGui_ImplSDL3_Shutdown();
        igDestroyContext(g_ctx);
        g_ctx = nullptr;
        return -3;
    }
    
    g_initialized = true;
    printf("[IMGUI] Initialized successfully\n");
    return 0;
}

void strata_imgui_shutdown(void) {
    if (!g_initialized) return;
    
    ImGui_ImplVulkan_Shutdown();
    ImGui_ImplSDL3_Shutdown();
    igDestroyContext(g_ctx);
    g_ctx = nullptr;
    g_initialized = false;
    printf("[IMGUI] Shutdown complete\n");
}

void strata_imgui_new_frame(void) {
    if (!g_initialized) return;
    
    ImGui_ImplVulkan_NewFrame();
    ImGui_ImplSDL3_NewFrame();
    igNewFrame();
}

void strata_imgui_render(void) {
    if (!g_initialized) return;
    igRender();
}

void strata_imgui_prepare_render(void) {
    if (!g_initialized) return;
    igRender();
}

void strata_imgui_render_to(void* commandBuffer) {
    if (!g_initialized || !commandBuffer) return;
    ImDrawData* drawData = igGetDrawData();
    if (!drawData || drawData->CmdLists.Size == 0) return;
    ImGui_ImplVulkan_RenderDrawData(drawData, (VkCommandBuffer)commandBuffer);
}

int strata_imgui_has_draw_data(void) {
    if (!g_initialized) return 0;
    ImDrawData* drawData = igGetDrawData();
    return (drawData && drawData->CmdLists.Size > 0) ? 1 : 0;
}

int strata_imgui_process_event(void* sdlEvent) {
    if (!g_initialized) return 0;
    
    SDL_Event* event = (SDL_Event*)sdlEvent;
    bool consumed = ImGui_ImplSDL3_ProcessEvent(event);
    return consumed ? 1 : 0;
}

int strata_imgui_want_capture_mouse(void) {
    if (!g_initialized) return 0;
    return igGetIO_Nil()->WantCaptureMouse ? 1 : 0;
}

int strata_imgui_want_capture_keyboard(void) {
    if (!g_initialized) return 0;
    return igGetIO_Nil()->WantCaptureKeyboard ? 1 : 0;
}

// ── Native Mods Screen ───────────────────────────────────────────────

static const char* g_modNames[] = {
    "Shader Pack v1.0",
    "Optimization Mod",
    "Biome Expansion",
    "Tool Collection",
    "Quest System"
};
static int g_modEnabled[] = {1, 0, 1, 0, 1};
static const int g_modCount = sizeof(g_modNames) / sizeof(g_modNames[0]);

static bool g_showEditor = false;
static bool g_showModsList = false;
static int g_lastAction = 0;

int strata_imgui_native_mods_screen(void) {
    if (!g_initialized) return 0;
    
    ImGuiIO* io = igGetIO_Nil();
    
    // Center the window
    ImVec2_c winSize = {400.0f, 300.0f};
    
    ImVec2_c pos = {io->DisplaySize.x / 2.0f - winSize.x / 2.0f,
                    io->DisplaySize.y / 2.0f - winSize.y / 2.0f};
    ImVec2_c pivot = {0.5f, 0.5f};
    igSetNextWindowPos(pos, ImGuiCond_FirstUseEver, pivot);
    igSetNextWindowSize(winSize, ImGuiCond_FirstUseEver);
    
    if (!igBegin("Native Mods Hub", nullptr,
                  ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove)) {
        igEnd();
        return 0;
    }
    
    // Title
    ImVec4_c titleColor = {0.3f, 0.7f, 1.0f, 1.0f};
    igTextColored(titleColor, "STRATA MODDING API v0.2.0");
    igSeparator();
    igText("Choose an action:");
    
    // Editor button
    ImVec2_c btnSize = {200.0f, 40.0f};
    if (igButton("EDITOR", btnSize)) {
        g_showEditor = true;
        g_showModsList = false;
        g_lastAction = 1;
    }
    igSameLine(0.0f, 0.0f);
    
    // Mods button
    if (igButton("MODS", btnSize)) {
        g_showEditor = false;
        g_showModsList = true;
        g_lastAction = 2;
    }
    
    igSeparator();
    
    // Status info
    igText("Loaded mods: %d / %d", 
           g_modEnabled[0] + g_modEnabled[1] + g_modEnabled[2] + g_modEnabled[3] + g_modEnabled[4],
           g_modCount);
    
    igSeparator();
    
    // Back button
    ImVec2_c backSize = {120.0f, 30.0f};
    if (igButton("< BACK", backSize)) {
        g_lastAction = 3;
    }
    
    igEnd();
    
    // Show editor panel if active
    if (g_showEditor) {
        strata_imgui_editor_panel_impl();
    }
    
    // Show mods list if active
    if (g_showModsList) {
        strata_imgui_mods_list_impl(g_modNames, g_modEnabled, g_modCount);
    }
    
    return g_lastAction;
}

static int strata_imgui_mods_list_impl(const char** modNames, int* modEnabled, int modCount) {
    if (!g_initialized) return 0;
    
    ImGuiIO* io = igGetIO_Nil();
    
    ImVec2_c pos = {io->DisplaySize.x / 2.0f - 350.0f,
                    io->DisplaySize.y / 2.0f - 250.0f};
    ImVec2_c pivot = {0.5f, 0.5f};
    igSetNextWindowPos(pos, ImGuiCond_Always, pivot);
    ImVec2_c size = {700.0f, 500.0f};
    igSetNextWindowSize(size, ImGuiCond_Always);
    
    if (!igBegin("Mods Manager", nullptr, ImGuiWindowFlags_None)) {
        igEnd();
        return 0;
    }
    
    igText("Manage your mods (enabled/disabled):");
    igSeparator();
    
    // Simple list of mods
    for (int i = 0; i < modCount && i < 16; ++i) {
        bool enabled = modEnabled[i] != 0;
        if (igCheckbox(modNames[i], &enabled)) {
            modEnabled[i] = enabled ? 1 : 0;
        }
    }
    
    igSeparator();
    
    ImVec2_c backSize = {100.0f, 30.0f};
    if (igButton("BACK", backSize)) {
        igEnd();
        return -1;
    }
    
    igEnd();
    return 0;
}

static void strata_imgui_editor_panel_impl(void) {
    if (!g_initialized) return;
    
    ImGuiIO* io = igGetIO_Nil();
    
    ImVec2_c pos = {io->DisplaySize.x / 2.0f - 500.0f,
                    io->DisplaySize.y / 2.0f - 350.0f};
    ImVec2_c pivot = {0.5f, 0.5f};
    igSetNextWindowPos(pos, ImGuiCond_Always, pivot);
    ImVec2_c size = {1000.0f, 700.0f};
    igSetNextWindowSize(size, ImGuiCond_Always);
    
    if (!igBegin("Mod Editor", nullptr, ImGuiWindowFlags_None)) {
        igEnd();
        return;
    }
    
    igText("Monaco Editor Integration (Coming Soon)");
    igSeparator();
    igText("This panel will integrate Monaco Editor for in-game mod scripting.");
    igText("Use Kotlin/Native + JS interop for full IDE features.");
    ImVec4_c warnColor = {1.0f, 0.8f, 0.0f, 1.0f};
    igTextColored(warnColor, "Note: Monaco requires web runtime or WASM compilation.");
    
    igSeparator();
    
    // Placeholder text area
    igText("Quick Edit (Plain Text):");
    static char buffer[1024] = "// Your mod code here...\n// Use Kotlin/Native APIs\n";
    ImVec2_c editorSize = {-1.0f, 400.0f};
    igInputTextMultiline("##edit", buffer, sizeof(buffer),
                         editorSize, ImGuiInputTextFlags_None, nullptr, nullptr);
    
    ImVec2_c saveSize = {120.0f, 30.0f};
    if (igButton("Save Mod", saveSize)) {
        ImVec4_c greenColor = {0.0f, 1.0f, 0.0f, 1.0f};
        igTextColored(greenColor, "Mod saved!");
    }
    igSameLine(0.0f, 0.0f);
    
    ImVec2_c backSize = {100.0f, 30.0f};
    if (igButton("BACK", backSize)) {
        g_showEditor = false;
    }
    
    igEnd();
}

int strata_imgui_is_initialized(void) {
    return g_initialized ? 1 : 0;
}

} // extern "C"
