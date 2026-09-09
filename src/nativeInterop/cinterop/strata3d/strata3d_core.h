/**
 * Strata3D Core Module
 * ====================
 * Vulkan instance, physical/logical device, surface, and VMA memory allocator.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 * Inspired by Minecraft's Blaze3D, it provides a clean abstraction over Vulkan.
 * 
 * Usage:
 *   Str3D_Context* ctx = str3d_create(window);
 *   // ... use ctx for all Vulkan operations ...
 *   str3d_destroy(ctx);
 */

#ifndef STRATA3D_CORE_H
#define STRATA3D_CORE_H

#include <vulkan/vulkan.h>
#include <vk_mem_alloc.h>
#include <SDL3/SDL.h>
#include <SDL3/SDL_vulkan.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Forward declarations
 * ========================================================================= */

typedef struct Str3D_Context Str3D_Context;

/* =========================================================================
 * GPU Properties (for HUD / diagnostics)
 * ========================================================================= */

typedef struct {
    char        deviceName[256];    /* GPU name (e.g. "Intel UHD Graphics 620") */
    uint32_t    apiVersion;         /* Vulkan API version (encoded) */
    uint32_t    driverVersion;      /* Driver version */
    uint32_t    vendorID;           /* PCI vendor ID */
    uint32_t    deviceID;           /* PCI device ID */
    uint64_t    vramTotal;          /* Total device-local memory (bytes) */
    uint64_t    vramUsed;           /* Currently allocated by VMA (bytes) */
    int         cpuCores;           /* Logical CPU cores */
    uint64_t    ramTotal;           /* System RAM (bytes) */
} Str3D_GpuInfo;

/* =========================================================================
 * Context — the heart of Strata3D
 * ========================================================================= */

struct Str3D_Context {
    /* SDL */
    SDL_Window*     window;
    
    /* Vulkan instance & surface */
    VkInstance       instance;
    VkSurfaceKHR     surface;
    
    /* Physical & logical device */
    VkPhysicalDevice physicalDevice;
    VkDevice         device;
    VkQueue          graphicsQueue;
    VkQueue          presentQueue;
    uint32_t         graphicsFamily;
    uint32_t         presentFamily;
    
    /* VMA Allocator */
    VmaAllocator     allocator;
    
    /* Function pointers (loaded at init) */
    PFN_vkGetInstanceProcAddr  getInstanceProcAddr;
    PFN_vkGetDeviceProcAddr    getDeviceProcAddr;
    
    /* GPU info */
    Str3D_GpuInfo    gpuInfo;
    
    /* State */
    bool             initialized;
};

/* =========================================================================
 * Core API
 * ========================================================================= */

/**
 * Creates a Str3D context from an SDL window.
 * Initializes Vulkan instance, surface, physical/logical device, and VMA.
 * Returns NULL on failure.
 */
static inline Str3D_Context* str3d_create(SDL_Window* window) {
    Str3D_Context* ctx = (Str3D_Context*)calloc(1, sizeof(Str3D_Context));
    if (!ctx) return NULL;
    
    ctx->window = window;
    ctx->getInstanceProcAddr = (PFN_vkGetInstanceProcAddr)SDL_Vulkan_GetVkGetInstanceProcAddr();
    if (!ctx->getInstanceProcAddr) {
        fprintf(stderr, "str3d_create: failed to get vkGetInstanceProcAddr\n");
        free(ctx);
        return NULL;
    }
    
    /* --- Vulkan Instance --- */
    VkApplicationInfo appInfo = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "Strata",
        .applicationVersion = VK_MAKE_VERSION(1, 0, 0),
        .pEngineName = "Strata3D",
        .engineVersion = VK_MAKE_VERSION(1, 0, 0),
        .apiVersion = VK_MAKE_VERSION(1, 3, 0),
    };
    
    uint32_t extCount = 0;
    const char* const* extensions = SDL_Vulkan_GetInstanceExtensions(&extCount);
    if (!extensions || extCount == 0) {
        fprintf(stderr, "str3d_create: failed to get Vulkan extensions\n");
        free(ctx);
        return NULL;
    }
    
    VkInstanceCreateInfo instCI = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &appInfo,
        .enabledExtensionCount = extCount,
        .ppEnabledExtensionNames = extensions,
    };
    
    if (vkCreateInstance(&instCI, NULL, &ctx->instance) != VK_SUCCESS) {
        fprintf(stderr, "str3d_create: vkCreateInstance failed\n");
        free(ctx);
        return NULL;
    }
    
    /* Reload proc addr from instance */
    ctx->getInstanceProcAddr = (PFN_vkGetInstanceProcAddr)vkGetInstanceProcAddr(ctx->instance, "vkGetInstanceProcAddr");
    ctx->getDeviceProcAddr = (PFN_vkGetDeviceProcAddr)ctx->getInstanceProcAddr(ctx->instance, "vkGetDeviceProcAddr");
    
    /* --- Surface --- */
    if (!SDL_Vulkan_CreateSurface(window, ctx->instance, NULL, &ctx->surface)) {
        fprintf(stderr, "str3d_create: SDL_Vulkan_CreateSurface failed: %s\n", SDL_GetError());
        vkDestroyInstance(ctx->instance, NULL);
        free(ctx);
        return NULL;
    }
    
    /* --- Physical Device --- */
    uint32_t devCount = 0;
    vkEnumeratePhysicalDevices(ctx->instance, &devCount, NULL);
    if (devCount == 0) {
        fprintf(stderr, "str3d_create: no Vulkan devices found\n");
        vkDestroySurfaceKHR(ctx->instance, ctx->surface, NULL);
        vkDestroyInstance(ctx->instance, NULL);
        free(ctx);
        return NULL;
    }
    
    VkPhysicalDevice* devices = (VkPhysicalDevice*)malloc(sizeof(VkPhysicalDevice) * devCount);
    vkEnumeratePhysicalDevices(ctx->instance, &devCount, devices);
    ctx->physicalDevice = devices[0]; /* Pick first GPU */
    
    /* Get GPU properties */
    VkPhysicalDeviceProperties devProps;
    vkGetPhysicalDeviceProperties(ctx->physicalDevice, &devProps);
    memcpy(ctx->gpuInfo.deviceName, devProps.deviceName, sizeof(ctx->gpuInfo.deviceName));
    ctx->gpuInfo.apiVersion = devProps.apiVersion;
    ctx->gpuInfo.driverVersion = devProps.driverVersion;
    ctx->gpuInfo.vendorID = devProps.vendorID;
    ctx->gpuInfo.deviceID = devProps.deviceID;
    
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(ctx->physicalDevice, &memProps);
    uint64_t totalVRAM = 0;
    for (uint32_t i = 0; i < memProps.memoryHeapCount; i++) {
        if (memProps.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) {
            totalVRAM += memProps.memoryHeaps[i].size;
        }
    }
    ctx->gpuInfo.vramTotal = totalVRAM;
    
    free(devices);
    
    /* --- Queue Family --- */
    uint32_t qfCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(ctx->physicalDevice, &qfCount, NULL);
    VkQueueFamilyProperties* qfProps = (VkQueueFamilyProperties*)malloc(sizeof(VkQueueFamilyProperties) * qfCount);
    vkGetPhysicalDeviceQueueFamilyProperties(ctx->physicalDevice, &qfCount, qfProps);
    
    ctx->graphicsFamily = UINT32_MAX;
    ctx->presentFamily = UINT32_MAX;
    
    for (uint32_t i = 0; i < qfCount; i++) {
        if ((qfProps[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && ctx->graphicsFamily == UINT32_MAX) {
            ctx->graphicsFamily = i;
        }
        VkBool32 presentSupport = false;
        vkGetPhysicalDeviceSurfaceSupportKHR(ctx->physicalDevice, i, ctx->surface, &presentSupport);
        if (presentSupport && ctx->presentFamily == UINT32_MAX) {
            ctx->presentFamily = i;
        }
        if (ctx->graphicsFamily != UINT32_MAX && ctx->presentFamily != UINT32_MAX) break;
    }
    free(qfProps);
    
    if (ctx->graphicsFamily == UINT32_MAX || ctx->presentFamily == UINT32_MAX) {
        fprintf(stderr, "str3d_create: no suitable queue family\n");
        vkDestroySurfaceKHR(ctx->instance, ctx->surface, NULL);
        vkDestroyInstance(ctx->instance, NULL);
        free(ctx);
        return NULL;
    }
    
    /* --- Logical Device --- */
    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo qCI[2];
    uint32_t queueCount = 0;
    
    qCI[queueCount++] = (VkDeviceQueueCreateInfo){
        .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = ctx->graphicsFamily,
        .queueCount = 1,
        .pQueuePriorities = &queuePriority,
    };
    if (ctx->presentFamily != ctx->graphicsFamily) {
        qCI[queueCount++] = (VkDeviceQueueCreateInfo){
            .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
            .queueFamilyIndex = ctx->presentFamily,
            .queueCount = 1,
            .pQueuePriorities = &queuePriority,
        };
    }
    
    const char* devExtensions[] = { VK_KHR_SWAPCHAIN_EXTENSION_NAME };
    VkDeviceCreateInfo devCI = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = queueCount,
        .pQueueCreateInfos = qCI,
        .enabledExtensionCount = 1,
        .ppEnabledExtensionNames = devExtensions,
    };
    
    if (vkCreateDevice(ctx->physicalDevice, &devCI, NULL, &ctx->device) != VK_SUCCESS) {
        fprintf(stderr, "str3d_create: vkCreateDevice failed\n");
        vkDestroySurfaceKHR(ctx->instance, ctx->surface, NULL);
        vkDestroyInstance(ctx->instance, NULL);
        free(ctx);
        return NULL;
    }
    
    vkGetDeviceQueue(ctx->device, ctx->graphicsFamily, 0, &ctx->graphicsQueue);
    vkGetDeviceQueue(ctx->device, ctx->presentFamily, 0, &ctx->presentQueue);
    
    /* --- VMA Allocator --- */
    VmaVulkanFunctions vulkanFunctions = {
        .vkGetInstanceProcAddr = ctx->getInstanceProcAddr,
        .vkGetDeviceProcAddr   = ctx->getDeviceProcAddr,
    };
    
    /* Instance-level functions */
    PFN_vkGetPhysicalDeviceProperties gpProps = 
        (PFN_vkGetPhysicalDeviceProperties)ctx->getInstanceProcAddr(ctx->instance, "vkGetPhysicalDeviceProperties");
    PFN_vkGetPhysicalDeviceMemoryProperties gpMemProps = 
        (PFN_vkGetPhysicalDeviceMemoryProperties)ctx->getInstanceProcAddr(ctx->instance, "vkGetPhysicalDeviceMemoryProperties");
    
    vulkanFunctions.vkGetPhysicalDeviceProperties = gpProps;
    vulkanFunctions.vkGetPhysicalDeviceMemoryProperties = gpMemProps;
    
    /* Device-level functions — must use vkGetDeviceProcAddr */
    #define GD(name) vulkanFunctions.name = (PFN_##name)ctx->getDeviceProcAddr(ctx->device, #name)
    GD(vkAllocateMemory);
    GD(vkFreeMemory);
    GD(vkMapMemory);
    GD(vkUnmapMemory);
    GD(vkBindBufferMemory);
    GD(vkBindImageMemory);
    GD(vkGetBufferMemoryRequirements);
    GD(vkGetImageMemoryRequirements);
    GD(vkCreateBuffer);
    GD(vkDestroyBuffer);
    GD(vkCreateImage);
    GD(vkDestroyImage);
    GD(vkCmdCopyBuffer);
    #undef GD
    
    VmaAllocatorCreateInfo allocatorCI = {
        .physicalDevice = ctx->physicalDevice,
        .device = ctx->device,
        .pVulkanFunctions = &vulkanFunctions,
        .instance = ctx->instance,
        .vulkanApiVersion = VK_MAKE_VERSION(1, 3, 0),
    };
    
    if (vmaCreateAllocator(&allocatorCI, &ctx->allocator) != VK_SUCCESS) {
        fprintf(stderr, "str3d_create: vmaCreateAllocator failed\n");
        vkDestroyDevice(ctx->device, NULL);
        vkDestroySurfaceKHR(ctx->instance, ctx->surface, NULL);
        vkDestroyInstance(ctx->instance, NULL);
        free(ctx);
        return NULL;
    }
    
    /* --- CPU / RAM info --- */
    ctx->gpuInfo.cpuCores = SDL_GetNumLogicalCPUCores();
    
    ctx->initialized = true;
    return ctx;
}

/**
 * Destroys the Str3D context and all associated resources.
 * All child objects (swapchains, pipelines, buffers) must be destroyed first.
 */
static inline void str3d_destroy(Str3D_Context* ctx) {
    if (!ctx) return;
    
    if (ctx->device) {
        vkDeviceWaitIdle(ctx->device);
    }
    
    if (ctx->allocator) {
        vmaDestroyAllocator(ctx->allocator);
    }
    if (ctx->device) {
        vkDestroyDevice(ctx->device, NULL);
    }
    if (ctx->surface) {
        vkDestroySurfaceKHR(ctx->instance, ctx->surface, NULL);
    }
    if (ctx->instance) {
        vkDestroyInstance(ctx->instance, NULL);
    }
    
    free(ctx);
}

/**
 * Returns the GPU info for display in the HUD.
 */
static inline const Str3D_GpuInfo* str3d_get_gpu_info(const Str3D_Context* ctx) {
    return &ctx->gpuInfo;
}

/**
 * Updates VRAM usage stats (call periodically for HUD display).
 */
static inline void str3d_update_gpu_stats(Str3D_Context* ctx) {
    VmaTotalStatistics stats;
    vmaCalculateStatistics(ctx->allocator, &stats);
    ctx->gpuInfo.vramUsed = stats.total.statistics.allocationBytes;
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_CORE_H */
