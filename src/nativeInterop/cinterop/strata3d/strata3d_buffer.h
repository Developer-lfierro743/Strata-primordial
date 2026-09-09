/**
 * Strata3D Buffer Module
 * ======================
 * VMA-backed buffer and image allocation for GPU resources.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 */

#ifndef STRATA3D_BUFFER_H
#define STRATA3D_BUFFER_H

#include "strata3d_core.h"

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Buffer — VMA-backed GPU buffer
 * ========================================================================= */

typedef struct {
    VkBuffer        buffer;
    VmaAllocation   allocation;
    VmaAllocator    allocator;       /* Back-reference for cleanup */
    VkDeviceSize    size;
    VkDeviceSize    used;            /* Bytes currently written */
    bool            mapped;
    void*           mappedPtr;
} Str3D_Buffer;

/**
 * Creates a GPU buffer with optional initial data upload.
 * 
 * usageFlags: VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, etc.
 * data: initial data to upload (may be NULL)
 * dataSize: size of data in bytes
 */
static inline Str3D_Buffer* str3d_buffer_create(
    Str3D_Context* ctx,
    VkDeviceSize size,
    VkBufferUsageFlags usageFlags,
    const void* data, VkDeviceSize dataSize
) {
    Str3D_Buffer* buf = (Str3D_Buffer*)calloc(1, sizeof(Str3D_Buffer));
    if (!buf) return NULL;
    
    buf->allocator = ctx->allocator;
    buf->size = size;
    buf->used = dataSize;
    
    VkBufferCreateInfo bufferCI = {
        .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = size,
        .usage = usageFlags | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
    };
    
    VmaAllocationCreateInfo allocCI = {
        .usage = VMA_MEMORY_USAGE_AUTO,
        .flags = VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT |
                 VMA_ALLOCATION_CREATE_HOST_ACCESS_ALLOW_TRANSFER_INSTEAD_BIT,
    };
    
    if (vmaCreateBuffer(ctx->allocator, &bufferCI, &allocCI, &buf->buffer, &buf->allocation, NULL) != VK_SUCCESS) {
        free(buf);
        return NULL;
    }
    
    /* Upload initial data if provided */
    if (data && dataSize > 0) {
        if (dataSize > size) dataSize = size;
        
        void* mapped = NULL;
        if (vmaMapMemory(ctx->allocator, buf->allocation, &mapped) == VK_SUCCESS) {
            memcpy(mapped, data, dataSize);
            vmaUnmapMemory(ctx->allocator, buf->allocation);
            buf->mappedPtr = mapped;
            buf->mapped = true;
        }
    }
    
    return buf;
}

/**
 * Updates buffer data (re-maps and copies).
 * Handles buffers that need to grow.
 */
static inline bool str3d_buffer_upload(
    Str3D_Context* ctx,
    Str3D_Buffer* buf,
    const void* data, VkDeviceSize dataSize
) {
    if (!buf || !data || dataSize == 0) return false;
    if (dataSize <= buf->size) {
        /* Fast path: buffer is large enough, just re-map and copy */
        void* mapped = NULL;
        if (vmaMapMemory(ctx->allocator, buf->allocation, &mapped) == VK_SUCCESS) {
            memcpy(mapped, data, dataSize);
            vmaUnmapMemory(ctx->allocator, buf->allocation);
            buf->used = dataSize;
            return true;
        }
    }
    
    /* Slow path: need to reallocate */
    vmaDestroyBuffer(ctx->allocator, buf->buffer, buf->allocation);
    
    buf->size = dataSize;
    VkBufferCreateInfo bufferCI = {
        .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = dataSize,
        .usage = buf->buffer ? VK_BUFFER_USAGE_VERTEX_BUFFER_BIT : VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
    };
    
    VmaAllocationCreateInfo allocCI = {
        .usage = VMA_MEMORY_USAGE_AUTO,
        .flags = VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT |
                 VMA_ALLOCATION_CREATE_HOST_ACCESS_ALLOW_TRANSFER_INSTEAD_BIT,
    };
    
    if (vmaCreateBuffer(ctx->allocator, &bufferCI, &allocCI, &buf->buffer, &buf->allocation, NULL) != VK_SUCCESS) {
        return false;
    }
    
    void* mapped = NULL;
    if (vmaMapMemory(ctx->allocator, buf->allocation, &mapped) == VK_SUCCESS) {
        memcpy(mapped, data, dataSize);
        vmaUnmapMemory(ctx->allocator, buf->allocation);
        buf->used = dataSize;
        return true;
    }
    
    return false;
}

/**
 * Destroys a GPU buffer and frees its memory.
 */
static inline void str3d_buffer_destroy(Str3D_Context* ctx, Str3D_Buffer* buf) {
    if (!ctx || !buf) return;
    if (buf->allocation) {
        vmaDestroyBuffer(ctx->allocator, buf->buffer, buf->allocation);
    }
    free(buf);
}

/* =========================================================================
 * Sky Buffer — pre-built fullscreen gradient quad
 * ========================================================================= */

/**
 * Creates the sky background vertex buffer.
 * 4 vertices forming a fullscreen quad with a vertical gradient.
 */
static inline Str3D_Buffer* str3d_sky_buffer_create(Str3D_Context* ctx) {
    /* 4 vertices: position(3) + color(3) + material(1) = 28 bytes each */
    float skyVerts[] = {
        /* top-left */     -1.0f, -1.0f, 0.0f,   0.52f, 0.76f, 0.92f,  0.0f,
        /* top-right */     1.0f, -1.0f, 0.0f,   0.52f, 0.76f, 0.92f,  0.0f,
        /* bottom-left */  -1.0f,  1.0f, 0.0f,   0.08f, 0.15f, 0.35f,  0.0f,
        /* bottom-right */  1.0f,  1.0f, 0.0f,   0.08f, 0.15f, 0.35f,  0.0f,
    };
    
    return str3d_buffer_create(ctx, sizeof(skyVerts),
                                VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                                skyVerts, sizeof(skyVerts));
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_BUFFER_H */
