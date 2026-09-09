/**
 * Strata3D Swapchain Module
 * =========================
 * Swapchain management, depth buffer, framebuffers, and synchronization.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 */

#ifndef STRATA3D_SWAPCHAIN_H
#define STRATA3D_SWAPCHAIN_H

#include "strata3d_core.h"

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Constants
 * ========================================================================= */

#define STR3D_MAX_FRAMES_IN_FLIGHT  3
#define STR3D_MAX_SWAPCHAIN_IMAGES  8

/* =========================================================================
 * Swapchain
 * ========================================================================= */

typedef struct {
    /* Vulkan handles */
    VkSwapchainKHR      swapchain;
    VkFormat            imageFormat;
    VkExtent2D          extent;
    
    /* Swapchain images & views */
    VkImage             images[STR3D_MAX_SWAPCHAIN_IMAGES];
    VkImageView         imageViews[STR3D_MAX_SWAPCHAIN_IMAGES];
    uint32_t            imageCount;
    
    /* Depth buffer (VMA-backed) */
    VkImage             depthImage;
    VkImageView         depthView;
    VmaAllocation       depthAllocation;
    VkFormat            depthFormat;
    
    /* Render pass & framebuffers */
    VkRenderPass        renderPass;
    VkFramebuffer       framebuffers[STR3D_MAX_SWAPCHAIN_IMAGES];
    
    /* Command buffers (one per swapchain image) */
    VkCommandPool       commandPool;
    VkCommandBuffer     commandBuffers[STR3D_MAX_SWAPCHAIN_IMAGES];
    
    /* Synchronization (ring buffer) */
    VkFence             fences[STR3D_MAX_FRAMES_IN_FLIGHT];
    VkSemaphore         imageAvailable[STR3D_MAX_FRAMES_IN_FLIGHT];
    VkSemaphore         renderFinished[STR3D_MAX_FRAMES_IN_FLIGHT];
    uint32_t            currentFrame;
    
    /* State */
    uint32_t            needsRebuild;
    uint32_t            currentImageIndex;
    
    /* Capture (screenshot) */
    VkBuffer            captureBuffer;
    VmaAllocation       captureAllocation;
    uint32_t            captureWidth;
    uint32_t            captureHeight;
    uint32_t            captureRequested;
    uint32_t            captureDone;
    uint32_t            captureAcked;
} Str3D_Swapchain;

/* =========================================================================
 * Internal helpers (used by strata3d_swapchain.h)
 * ========================================================================= */

static inline bool str3d__create_depth_buffer(
    Str3D_Context* ctx, Str3D_Swapchain* sc
) {
    VkImageCreateInfo ci = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
        .imageType = VK_IMAGE_TYPE_2D,
        .format = sc->depthFormat,
        .extent = { sc->extent.width, sc->extent.height, 1 },
        .mipLevels = 1,
        .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = VK_IMAGE_TILING_OPTIMAL,
        .usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
    };
    
    VmaAllocationCreateInfo allocCI = {
        .usage = VMA_MEMORY_USAGE_AUTO,
        .flags = VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT,
    };
    
    if (vmaCreateImage(ctx->allocator, &ci, &allocCI, &sc->depthImage, &sc->depthAllocation, NULL) != VK_SUCCESS) {
        return false;
    }
    
    VkImageViewCreateInfo viewCI = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
        .image = sc->depthImage,
        .viewType = VK_IMAGE_VIEW_TYPE_2D,
        .format = sc->depthFormat,
        .subresourceRange = {
            .aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT,
            .baseMipLevel = 0,
            .levelCount = 1,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
    };
    
    PFN_vkCreateImageView createImageView = 
        (PFN_vkCreateImageView)ctx->getInstanceProcAddr(ctx->instance, "vkCreateImageView");
    return createImageView(ctx->device, &viewCI, NULL, &sc->depthView) == VK_SUCCESS;
}

/* =========================================================================
 * Public Swapchain API
 * ========================================================================= */

/**
 * Creates a new swapchain with depth buffer, render pass, framebuffers,
 * command buffers, and synchronization primitives.
 */
static inline Str3D_Swapchain* str3d_swapchain_create(
    Str3D_Context* ctx, uint32_t width, uint32_t height
) {
    Str3D_Swapchain* sc = (Str3D_Swapchain*)calloc(1, sizeof(Str3D_Swapchain));
    if (!sc) return NULL;
    
    PFN_vkGetInstanceProcAddr gpAddr = ctx->getInstanceProcAddr;
    
    /* Query surface capabilities */
    VkSurfaceCapabilitiesKHR caps;
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(ctx->physicalDevice, ctx->surface, &caps);
    
    VkExtent2D extent = caps.currentExtent;
    if (extent.width == UINT32_MAX) {
        extent.width = width;
        extent.height = height;
    }
    extent.width = extent.width < caps.minImageExtent.width ? caps.minImageExtent.width : 
                   (extent.width > caps.maxImageExtent.width ? caps.maxImageExtent.width : extent.width);
    extent.height = extent.height < caps.minImageExtent.height ? caps.minImageExtent.height :
                    (extent.height > caps.maxImageExtent.height ? caps.maxImageExtent.height : extent.height);
    sc->extent = extent;
    
    /* Choose surface format */
    uint32_t fmtCount;
    vkGetPhysicalDeviceSurfaceFormatsKHR(ctx->physicalDevice, ctx->surface, &fmtCount, NULL);
    VkSurfaceFormatKHR* formats = (VkSurfaceFormatKHR*)malloc(sizeof(VkSurfaceFormatKHR) * fmtCount);
    vkGetPhysicalDeviceSurfaceFormatsKHR(ctx->physicalDevice, ctx->surface, &fmtCount, formats);
    
    sc->imageFormat = formats[0].format;
    VkColorSpaceKHR colorSpace = formats[0].colorSpace;
    for (uint32_t i = 0; i < fmtCount; i++) {
        if (formats[i].format == VK_FORMAT_B8G8R8A8_UNORM) {
            sc->imageFormat = formats[i].format;
            colorSpace = formats[i].colorSpace;
            break;
        }
    }
    free(formats);
    
    /* Create swapchain */
    uint32_t imageCount = caps.minImageCount + 1;
    if (caps.maxImageCount > 0 && imageCount > caps.maxImageCount) {
        imageCount = caps.maxImageCount;
    }
    
    VkSwapchainCreateInfoKHR scCI = {
        .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
        .surface = ctx->surface,
        .minImageCount = imageCount,
        .imageFormat = sc->imageFormat,
        .imageColorSpace = colorSpace,
        .imageExtent = extent,
        .imageArrayLayers = 1,
        .imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
    };
    
    uint32_t queueFamilyIndices[] = { ctx->graphicsFamily, ctx->presentFamily };
    if (ctx->graphicsFamily != ctx->presentFamily) {
        scCI.imageSharingMode = VK_SHARING_MODE_CONCURRENT;
        scCI.queueFamilyIndexCount = 2;
        scCI.pQueueFamilyIndices = queueFamilyIndices;
    } else {
        scCI.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    }
    
    scCI.preTransform = caps.currentTransform;
    scCI.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    scCI.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    scCI.clipped = VK_TRUE;
    scCI.oldSwapchain = VK_NULL_HANDLE;
    
    PFN_vkCreateSwapchainKHR createSwapchain = 
        (PFN_vkCreateSwapchainKHR)gpAddr(ctx->instance, "vkCreateSwapchainKHR");
    if (createSwapchain(ctx->device, &scCI, NULL, &sc->swapchain) != VK_SUCCESS) {
        free(sc);
        return NULL;
    }
    
    /* Get swapchain images */
    vkGetSwapchainImagesKHR(ctx->device, sc->swapchain, &sc->imageCount, NULL);
    vkGetSwapchainImagesKHR(ctx->device, sc->swapchain, &sc->imageCount, sc->images);
    
    /* Create image views */
    PFN_vkCreateImageView createImageView = 
        (PFN_vkCreateImageView)gpAddr(ctx->instance, "vkCreateImageView");
    for (uint32_t i = 0; i < sc->imageCount; i++) {
        VkImageViewCreateInfo viewCI = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
            .image = sc->images[i],
            .viewType = VK_IMAGE_VIEW_TYPE_2D,
            .format = sc->imageFormat,
            .components = { VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                           VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY },
            .subresourceRange = {
                .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                .baseMipLevel = 0, .levelCount = 1,
                .baseArrayLayer = 0, .layerCount = 1,
            },
        };
        createImageView(ctx->device, &viewCI, NULL, &sc->imageViews[i]);
    }
    
    /* Depth buffer */
    sc->depthFormat = VK_FORMAT_D32_SFLOAT;
    if (!str3d__create_depth_buffer(ctx, sc)) {
        fprintf(stderr, "str3d_swapchain_create: depth buffer failed\n");
    }
    
    /* Render pass */
    VkAttachmentDescription attachments[2] = {
        { /* Color */
            .format = sc->imageFormat,
            .samples = VK_SAMPLE_COUNT_1_BIT,
            .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
            .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
            .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
            .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
            .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
            .finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
        },
        { /* Depth */
            .format = sc->depthFormat,
            .samples = VK_SAMPLE_COUNT_1_BIT,
            .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
            .storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
            .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
            .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
            .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
            .finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
        },
    };
    
    VkAttachmentReference colorRef = { 0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
    VkAttachmentReference depthRef = { 1, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL };
    
    VkSubpassDescription subpass = {
        .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1,
        .pColorAttachments = &colorRef,
        .pDepthStencilAttachment = &depthRef,
    };
    
    VkSubpassDependency dependency = {
        .srcSubpass = VK_SUBPASS_EXTERNAL,
        .dstSubpass = 0,
        .srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
        .dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
        .srcAccessMask = 0,
        .dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
    };
    
    VkRenderPassCreateInfo rpCI = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 2,
        .pAttachments = attachments,
        .subpassCount = 1,
        .pSubpasses = &subpass,
        .dependencyCount = 1,
        .pDependencies = &dependency,
    };
    
    PFN_vkCreateRenderPass createRenderPass = 
        (PFN_vkCreateRenderPass)gpAddr(ctx->instance, "vkCreateRenderPass");
    createRenderPass(ctx->device, &rpCI, NULL, &sc->renderPass);
    
    /* Framebuffers */
    PFN_vkCreateFramebuffer createFramebuffer = 
        (PFN_vkCreateFramebuffer)gpAddr(ctx->instance, "vkCreateFramebuffer");
    for (uint32_t i = 0; i < sc->imageCount; i++) {
        VkImageView fbAttachments[] = { sc->imageViews[i], sc->depthView };
        VkFramebufferCreateInfo fbCI = {
            .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
            .renderPass = sc->renderPass,
            .attachmentCount = 2,
            .pAttachments = fbAttachments,
            .width = extent.width,
            .height = extent.height,
            .layers = 1,
        };
        createFramebuffer(ctx->device, &fbCI, NULL, &sc->framebuffers[i]);
    }
    
    /* Command pool & buffers */
    VkCommandPoolCreateInfo poolCI = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
        .queueFamilyIndex = ctx->graphicsFamily,
    };
    PFN_vkCreateCommandPool createCommandPool = 
        (PFN_vkCreateCommandPool)gpAddr(ctx->instance, "vkCreateCommandPool");
    createCommandPool(ctx->device, &poolCI, NULL, &sc->commandPool);
    
    VkCommandBufferAllocateInfo allocBI = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = sc->commandPool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = sc->imageCount,
    };
    PFN_vkAllocateCommandBuffers allocateCommandBuffers = 
        (PFN_vkAllocateCommandBuffers)gpAddr(ctx->instance, "vkAllocateCommandBuffers");
    allocateCommandBuffers(ctx->device, &allocBI, sc->commandBuffers);
    
    /* Synchronization */
    VkFenceCreateInfo fenceCI = {
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
        .flags = VK_FENCE_CREATE_SIGNALED_BIT,
    };
    VkSemaphoreCreateInfo semCI = {
        .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
    };
    
    PFN_vkCreateFence createFence = (PFN_vkCreateFence)gpAddr(ctx->instance, "vkCreateFence");
    PFN_vkCreateSemaphore createSemaphore = (PFN_vkCreateSemaphore)gpAddr(ctx->instance, "vkCreateSemaphore");
    
    for (uint32_t i = 0; i < STR3D_MAX_FRAMES_IN_FLIGHT; i++) {
        createFence(ctx->device, &fenceCI, NULL, &sc->fences[i]);
        createSemaphore(ctx->device, &semCI, NULL, &sc->imageAvailable[i]);
        createSemaphore(ctx->device, &semCI, NULL, &sc->renderFinished[i]);
    }
    
    sc->currentFrame = 0;
    sc->needsRebuild = 0;
    return sc;
}

/**
 * Destroys the swapchain and all associated resources.
 */
static inline void str3d_swapchain_destroy(Str3D_Context* ctx, Str3D_Swapchain* sc) {
    if (!ctx || !sc) return;
    
    vkDeviceWaitIdle(ctx->device);
    
    PFN_vkGetInstanceProcAddr gpAddr = ctx->getInstanceProcAddr;
    PFN_vkDestroyFence destroyFence = (PFN_vkDestroyFence)gpAddr(ctx->instance, "vkDestroyFence");
    PFN_vkDestroySemaphore destroySemaphore = (PFN_vkDestroySemaphore)gpAddr(ctx->instance, "vkDestroySemaphore");
    PFN_vkDestroyFramebuffer destroyFramebuffer = (PFN_vkDestroyFramebuffer)gpAddr(ctx->instance, "vkDestroyFramebuffer");
    PFN_vkDestroyRenderPass destroyRenderPass = (PFN_vkDestroyRenderPass)gpAddr(ctx->instance, "vkDestroyRenderPass");
    PFN_vkDestroyImageView destroyImageView = (PFN_vkDestroyImageView)gpAddr(ctx->instance, "vkDestroyImageView");
    PFN_vkDestroyCommandPool destroyCommandPool = (PFN_vkDestroyCommandPool)gpAddr(ctx->instance, "vkDestroyCommandPool");
    PFN_vkDestroySwapchainKHR destroySwapchain = (PFN_vkDestroySwapchainKHR)gpAddr(ctx->instance, "vkDestroySwapchainKHR");
    
    for (uint32_t i = 0; i < STR3D_MAX_FRAMES_IN_FLIGHT; i++) {
        destroyFence(ctx->device, sc->fences[i], NULL);
        destroySemaphore(ctx->device, sc->imageAvailable[i], NULL);
        destroySemaphore(ctx->device, sc->renderFinished[i], NULL);
    }
    
    for (uint32_t i = 0; i < sc->imageCount; i++) {
        destroyFramebuffer(ctx->device, sc->framebuffers[i], NULL);
    }
    destroyRenderPass(ctx->device, sc->renderPass, NULL);
    
    for (uint32_t i = 0; i < sc->imageCount; i++) {
        destroyImageView(ctx->device, sc->imageViews[i], NULL);
    }
    
    /* Depth buffer */
    if (sc->depthView) destroyImageView(ctx->device, sc->depthView, NULL);
    if (sc->depthAllocation) vmaDestroyImage(ctx->allocator, sc->depthImage, sc->depthAllocation);
    
    destroyCommandPool(ctx->device, sc->commandPool, NULL);
    destroySwapchain(ctx->device, sc->swapchain, NULL);
    
    free(sc);
}

/**
 * Returns true if the swapchain needs to be recreated (e.g. after resize).
 */
static inline bool str3d_swapchain_needs_rebuild(Str3D_Swapchain* sc) {
    if (sc && sc->needsRebuild) {
        sc->needsRebuild = 0;
        return true;
    }
    return false;
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_SWAPCHAIN_H */
