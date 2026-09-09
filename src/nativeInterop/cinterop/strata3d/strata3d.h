/**
 * Strata3D — Proprietary Rendering Library
 * =========================================
 * 
 * A high-performance Vulkan rendering abstraction for the Strata voxel engine.
 * Inspired by Minecraft's Blaze3D, Strata3D provides a clean, modular API over
 * Vulkan and SDL3, optimized for integrated GPUs (iGPUs).
 * 
 * Architecture:
 *   strata3d_math.h     — Vector and matrix math
 *   strata3d_core.h     — Vulkan instance, device, surface, VMA allocator
 *   strata3d_swapchain.h — Swapchain, depth buffer, framebuffers, sync
 *   strata3d_pipeline.h — Graphics pipelines, shaders, vertex layouts
 *   strata3d_buffer.h   — VMA-backed buffer allocation
 *   strata3d_renderer.h — High-level frame rendering
 *   strata3d_camera.h   — First-person camera
 *   strata3d_input.h    — SDL3 input abstraction
 * 
 * Usage:
 *   // Create context
 *   Str3D_Context* ctx = str3d_create(window);
 *   
 *   // Create swapchain
 *   Str3D_Swapchain* sc = str3d_swapchain_create(ctx, width, height);
 *   
 *   // Create pipeline
 *   Str3D_Pipeline* pl = str3d_pipeline_create_world(ctx, sc, vertSpv, sizeof(vertSpv), fragSpv, sizeof(fragSpv));
 *   
 *   // Create buffer
 *   Str3D_Buffer* buf = str3d_buffer_create(ctx, size, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, data, dataSize);
 *   
 *   // Render frame
 *   VkCommandBuffer cmd = str3d_renderer_begin(ctx, sc, 0.52f, 0.76f, 0.92f);
 *   // ... record draw calls ...
 *   str3d_renderer_end(ctx, sc);
 *   
 *   // Cleanup
 *   str3d_buffer_destroy(ctx, buf);
 *   str3d_pipeline_destroy(ctx, pl);
 *   str3d_swapchain_destroy(ctx, sc);
 *   str3d_destroy(ctx);
 * 
 * Strata3D is copyright (c) Strata Engine. All rights reserved.
 */

#ifndef STRATA3D_H
#define STRATA3D_H

/* Include all Strata3D modules */
#include "strata3d_math.h"
#include "strata3d_core.h"
#include "strata3d_swapchain.h"
#include "strata3d_pipeline.h"
#include "strata3d_buffer.h"
#include "strata3d_renderer.h"
#include "strata3d_camera.h"
#include "strata3d_input.h"

#endif /* STRATA3D_H */
