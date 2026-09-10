/**
 * Str3D Renderer Module
 * =====================
 * High-level frame rendering: acquire, record, submit, present.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 */

#ifndef STRATA3D_RENDERER_H
#define STRATA3D_RENDERER_H

#include "strata3d_core.h"
#include "strata3d_swapchain.h"
#include "strata3d_pipeline.h"
#include "strata3d_buffer.h"

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Draw Command — recorded during frame rendering
 * ========================================================================= */

#define STR3D_MAX_DRAW_CALLS 256

typedef struct {
    Str3D_Pipeline*     pipeline;
    Str3D_Buffer*       vertexBuffer;
    uint32_t            vertexOffset;
    uint32_t            vertexCount;
    float               pushConstants[80];
} Str3D_DrawCommand;

/* =========================================================================
 * Renderer State
 * ========================================================================= */

typedef struct {
    Str3D_DrawCommand   draws[STR3D_MAX_DRAW_CALLS];
    uint32_t            drawCount;
} Str3D_Renderer;

/* =========================================================================
 * Screenshot BMP Writer
 * ========================================================================= */

static inline void str3d__write_bmp(const char* path, const uint8_t* data, uint32_t w, uint32_t h) {
    uint32_t rowSize = (w * 3 + 3) & ~3u;
    uint32_t imgSize = rowSize * h;
    uint32_t fileSize = 54 + imgSize;
    
    FILE* f = fopen(path, "wb");
    if (!f) return;
    
    uint8_t header[54];
    memset(header, 0, 54);
    header[0] = 'B'; header[1] = 'M';
    header[2] = (uint8_t)(fileSize); header[3] = (uint8_t)(fileSize >> 8);
    header[4] = (uint8_t)(fileSize >> 16); header[5] = (uint8_t)(fileSize >> 24);
    header[10] = 54; header[14] = 40;
    header[18] = (uint8_t)(w); header[19] = (uint8_t)(w >> 8); header[20] = (uint8_t)(w >> 16); header[21] = (uint8_t)(w >> 24);
    header[22] = (uint8_t)(h); header[23] = (uint8_t)(h >> 8); header[24] = (uint8_t)(h >> 16); header[25] = (uint8_t)(h >> 24);
    header[26] = 1; header[28] = 24; header[34] = (uint8_t)(imgSize);
    
    fwrite(header, 1, 54, f);
    
    uint32_t x, y;
    int yi;
    for (yi = (int)h - 1; yi >= 0; yi--) {
        y = (uint32_t)yi;
        const uint8_t* row = data + y * w * 4;
        for (x = 0; x < w; x++) {
            uint8_t bgr[3];
            bgr[0] = row[x * 4 + 2];
            bgr[1] = row[x * 4 + 1];
            bgr[2] = row[x * 4];
            fwrite(bgr, 1, 3, f);
        }
        uint8_t pad[3];
        memset(pad, 0, 3);
        fwrite(pad, 1, rowSize - w * 3, f);
    }
    
    fclose(f);
}

/* =========================================================================
 * Frame Rendering API
 * ========================================================================= */

/**
 * Begins a new render frame.
 * Acquires the next swapchain image, waits for the fence, and begins recording.
 * Returns the command buffer for this frame, or NULL if swapchain needs rebuild.
 */
static inline VkCommandBuffer str3d_renderer_begin(
    Str3D_Context* ctx,
    Str3D_Swapchain* sc,
    float clearR, float clearG, float clearB
) {
    PFN_vkGetInstanceProcAddr gpAddr = ctx->getInstanceProcAddr;
    uint32_t frame = sc->currentFrame;
    
    /* Wait for previous frame with this index to finish */
    PFN_vkWaitForFences waitForFences = (PFN_vkWaitForFences)gpAddr(ctx->instance, "vkWaitForFences");
    waitForFences(ctx->device, 1, &sc->fences[frame], VK_TRUE, UINT64_MAX);
    
    /* Acquire next image */
    PFN_vkAcquireNextImageKHR acquireNextImage = 
        (PFN_vkAcquireNextImageKHR)gpAddr(ctx->instance, "vkAcquireNextImageKHR");
    
    uint32_t imageIndex;
    VkResult acquireResult = acquireNextImage(
        ctx->device, sc->swapchain, UINT64_MAX,
        sc->imageAvailable[frame], VK_NULL_HANDLE, &imageIndex
    );
    
    if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR || acquireResult == VK_SUBOPTIMAL_KHR) {
        sc->needsRebuild = 1;
        return NULL;
    }
    if (acquireResult != VK_SUCCESS) {
        sc->needsRebuild = 1;
        return NULL;
    }
    
    sc->currentImageIndex = imageIndex;
    
    /* Reset fence */
    PFN_vkResetFences resetFences = (PFN_vkResetFences)gpAddr(ctx->instance, "vkResetFences");
    resetFences(ctx->device, 1, &sc->fences[frame]);
    
    /* Begin command buffer */
    VkCommandBuffer cmd = sc->commandBuffers[imageIndex];
    PFN_vkResetCommandBuffer resetCommandBuffer = 
        (PFN_vkResetCommandBuffer)gpAddr(ctx->instance, "vkResetCommandBuffer");
    resetCommandBuffer(cmd, 0);
    
    VkCommandBufferBeginInfo beginInfo;
    memset(&beginInfo, 0, sizeof(beginInfo));
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    
    PFN_vkBeginCommandBuffer beginCommandBuffer = 
        (PFN_vkBeginCommandBuffer)gpAddr(ctx->instance, "vkBeginCommandBuffer");
    beginCommandBuffer(cmd, &beginInfo);
    
    /* Begin render pass */
    VkClearValue clearValues[2];
    memset(clearValues, 0, sizeof(clearValues));
    clearValues[0].color.float32[0] = clearR;
    clearValues[0].color.float32[1] = clearG;
    clearValues[0].color.float32[2] = clearB;
    clearValues[0].color.float32[3] = 1.0f;
    clearValues[1].depthStencil.depth = 1.0f;
    clearValues[1].depthStencil.stencil = 0;
    
    VkRenderPassBeginInfo rpBI;
    memset(&rpBI, 0, sizeof(rpBI));
    rpBI.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    rpBI.renderPass = sc->renderPass;
    rpBI.framebuffer = sc->framebuffers[imageIndex];
    rpBI.renderArea.offset.x = 0;
    rpBI.renderArea.offset.y = 0;
    rpBI.renderArea.extent = sc->extent;
    rpBI.clearValueCount = 2;
    rpBI.pClearValues = clearValues;
    
    PFN_vkCmdBeginRenderPass cmdBeginRenderPass = 
        (PFN_vkCmdBeginRenderPass)gpAddr(ctx->instance, "vkCmdBeginRenderPass");
    cmdBeginRenderPass(cmd, &rpBI, VK_SUBPASS_CONTENTS_INLINE);
    
    /* Set viewport & scissor */
    VkViewport viewport;
    memset(&viewport, 0, sizeof(viewport));
    viewport.x = 0.0f;
    viewport.y = 0.0f;
    viewport.width = (float)sc->extent.width;
    viewport.height = (float)sc->extent.height;
    viewport.minDepth = 0.0f;
    viewport.maxDepth = 1.0f;
    
    VkRect2D scissor;
    memset(&scissor, 0, sizeof(scissor));
    scissor.offset.x = 0;
    scissor.offset.y = 0;
    scissor.extent = sc->extent;
    
    PFN_vkCmdSetViewport cmdSetViewport = (PFN_vkCmdSetViewport)gpAddr(ctx->instance, "vkCmdSetViewport");
    PFN_vkCmdSetScissor cmdSetScissor = (PFN_vkCmdSetScissor)gpAddr(ctx->instance, "vkCmdSetScissor");
    cmdSetViewport(cmd, 0, 1, &viewport);
    cmdSetScissor(cmd, 0, 1, &scissor);
    
    /* Handle screenshot capture setup */
    if (sc->captureRequested && !sc->captureDone) {
        VkDeviceSize captureSize = (VkDeviceSize)sc->extent.width * sc->extent.height * 4;
        if (sc->captureBuffer == VK_NULL_HANDLE || 
            sc->captureWidth != sc->extent.width || 
            sc->captureHeight != sc->extent.height) {
            if (sc->captureBuffer != VK_NULL_HANDLE) {
                vmaDestroyBuffer(ctx->allocator, sc->captureBuffer, sc->captureAllocation);
                sc->captureBuffer = VK_NULL_HANDLE;
            }
            
            VkBufferCreateInfo bufCI;
            memset(&bufCI, 0, sizeof(bufCI));
            bufCI.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
            bufCI.size = captureSize;
            bufCI.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            bufCI.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            
            VmaAllocationCreateInfo allocCI;
            memset(&allocCI, 0, sizeof(allocCI));
            allocCI.usage = VMA_MEMORY_USAGE_AUTO;
            allocCI.flags = VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT;
            
            vmaCreateBuffer(ctx->allocator, &bufCI, &allocCI, &sc->captureBuffer, &sc->captureAllocation, NULL);
            sc->captureWidth = sc->extent.width;
            sc->captureHeight = sc->extent.height;
        }
    }
    
    return cmd;
}

/**
 * Ends the render frame.
 * Submits the command buffer and presents the image.
 */
static inline void str3d_renderer_end(
    Str3D_Context* ctx,
    Str3D_Swapchain* sc
) {
    PFN_vkGetInstanceProcAddr gpAddr = ctx->getInstanceProcAddr;
    VkCommandBuffer cmd = sc->commandBuffers[sc->currentImageIndex];
    uint32_t frame = sc->currentFrame;
    
    /* Handle capture: copy image to buffer */
    if (sc->captureRequested && !sc->captureDone && sc->captureBuffer != VK_NULL_HANDLE) {
        PFN_vkCmdPipelineBarrier cmdBarrier = 
            (PFN_vkCmdPipelineBarrier)gpAddr(ctx->instance, "vkCmdPipelineBarrier");
        
        VkImageMemoryBarrier barrier;
        memset(&barrier, 0, sizeof(barrier));
        barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        barrier.oldLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = sc->images[sc->currentImageIndex];
        barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        barrier.subresourceRange.baseMipLevel = 0;
        barrier.subresourceRange.levelCount = 1;
        barrier.subresourceRange.baseArrayLayer = 0;
        barrier.subresourceRange.layerCount = 1;
        
        cmdBarrier(cmd, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                   VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1, &barrier);
        
        PFN_vkCmdCopyImageToBuffer cmdCopy = 
            (PFN_vkCmdCopyImageToBuffer)gpAddr(ctx->instance, "vkCmdCopyImageToBuffer");
        
        VkBufferImageCopy region;
        memset(&region, 0, sizeof(region));
        region.bufferOffset = 0;
        region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        region.imageSubresource.mipLevel = 0;
        region.imageSubresource.baseArrayLayer = 0;
        region.imageSubresource.layerCount = 1;
        region.imageExtent.width = sc->extent.width;
        region.imageExtent.height = sc->extent.height;
        region.imageExtent.depth = 1;
        
        cmdCopy(cmd, sc->captureBuffer, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                sc->images[sc->currentImageIndex], 1, &region);
        
        /* Restore layout */
        barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        barrier.newLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        barrier.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        cmdBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                   VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, 0, NULL, 0, NULL, 1, &barrier);
    }
    
    /* End render pass */
    PFN_vkCmdEndRenderPass cmdEndRenderPass = 
        (PFN_vkCmdEndRenderPass)gpAddr(ctx->instance, "vkCmdEndRenderPass");
    cmdEndRenderPass(cmd);
    
    /* End command buffer */
    PFN_vkEndCommandBuffer endCommandBuffer = 
        (PFN_vkEndCommandBuffer)gpAddr(ctx->instance, "vkEndCommandBuffer");
    endCommandBuffer(cmd);
    
    /* Submit */
    VkSemaphore waitSemaphores[1];
    VkPipelineStageFlags waitStages[1];
    VkSemaphore signalSemaphores[1];
    
    waitSemaphores[0] = sc->imageAvailable[frame];
    waitStages[0] = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    signalSemaphores[0] = sc->renderFinished[frame];
    
    VkSubmitInfo submitInfo;
    memset(&submitInfo, 0, sizeof(submitInfo));
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmd;
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;
    
    PFN_vkQueueSubmit queueSubmit = (PFN_vkQueueSubmit)gpAddr(ctx->instance, "vkQueueSubmit");
    queueSubmit(ctx->graphicsQueue, 1, &submitInfo, sc->fences[frame]);
    
    /* Present */
    VkPresentInfoKHR presentInfo;
    memset(&presentInfo, 0, sizeof(presentInfo));
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &sc->swapchain;
    presentInfo.pImageIndices = &sc->currentImageIndex;
    
    PFN_vkQueuePresentKHR queuePresent = 
        (PFN_vkQueuePresentKHR)gpAddr(ctx->instance, "vkQueuePresentKHR");
    VkResult presentResult = queuePresent(ctx->presentQueue, &presentInfo);
    
    if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
        sc->needsRebuild = 1;
    }
    
    /* Finalize capture — write BMP */
    if (sc->captureRequested && !sc->captureDone && sc->captureBuffer != VK_NULL_HANDLE) {
        void* mapped = NULL;
        if (vmaMapMemory(ctx->allocator, sc->captureAllocation, &mapped) == VK_SUCCESS) {
            str3d__write_bmp("screenshot.bmp", (const uint8_t*)mapped, sc->captureWidth, sc->captureHeight);
            vmaUnmapMemory(ctx->allocator, sc->captureAllocation);
        }
        sc->captureDone = 1;
    }
    
    sc->currentFrame = (sc->currentFrame + 1) % STR3D_MAX_FRAMES_IN_FLIGHT;
}

/**
 * Requests a screenshot capture on the next frame.
 */
static inline void str3d_renderer_capture(Str3D_Swapchain* sc) {
    if (sc) {
        sc->captureRequested = 1;
        sc->captureDone = 0;
        sc->captureAcked = 0;
    }
}

/**
 * Checks if a capture is done (screenshot has been written).
 */
static inline bool str3d_renderer_capture_done(const Str3D_Swapchain* sc) {
    return sc && sc->captureDone != 0;
}

/**
 * Acknowledges a completed capture.
 */
static inline void str3d_renderer_capture_ack(Str3D_Swapchain* sc) {
    if (sc) {
        sc->captureAcked = 1;
        sc->captureRequested = 0;
    }
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_RENDERER_H */
