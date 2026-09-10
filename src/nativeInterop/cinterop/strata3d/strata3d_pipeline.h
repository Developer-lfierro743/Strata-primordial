/**
 * Strata3D Pipeline Module
 * ========================
 * Graphics pipeline creation, shader modules, vertex input layouts.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 */

#ifndef STRATA3D_PIPELINE_H
#define STRATA3D_PIPELINE_H

#include "strata3d_core.h"
#include "strata3d_swapchain.h"

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Push Constants — shared across all pipelines
 * ========================================================================= */

#define STR3D_PUSH_CONST_SIZE 80  /* MVP(64) + cameraPos(12) + padding(4) */

/**
 * Push constant data block.
 * Passed to shaders via vkCmdPushConstants.
 * 
 * Layout:
 *   offset  0: MVP matrix (mat4, 64 bytes)
 *   offset 64: Camera position (vec3, 12 bytes)
 *   offset 76: Time/padding (float, 4 bytes)
 */
typedef struct {
    float mvp[16];          /* Model-View-Projection matrix */
    float cameraPos[3];     /* World-space camera position */
    float time;             /* Elapsed time in seconds */
} Str3D_PushConstants;

/* =========================================================================
 * Pipeline
 * ========================================================================= */

typedef struct {
    VkPipeline          pipeline;
    VkPipelineLayout    layout;
    VkShaderModule      vertModule;
    VkShaderModule      fragModule;
    
    /* Vertex layout info (for documentation/bindind) */
    uint32_t            vertexStride;   /* Bytes per vertex */
    uint32_t            vertexCount;    /* Number of vertices in buffer */
} Str3D_Pipeline;

/* =========================================================================
 * Pipeline Creation
 * ========================================================================= */

/**
 * Creates a graphics pipeline with the given SPIR-V shader bytecode.
 * 
 * Vertex format (28 bytes):
 *   offset  0: position (vec3, 12 bytes)
 *   offset 12: color    (vec3, 12 bytes)
 *   offset 24: material (float, 4 bytes)
 * 
 * Parameters:
 *   depthTest  - enable depth testing
 *   blend      - enable alpha blending
 *   depthWrite - enable depth writes
 *   waterPass  - if true, uses alpha blending for water transparency
 */
static inline Str3D_Pipeline* str3d_pipeline_create(
    Str3D_Context* ctx,
    Str3D_Swapchain* sc,
    const uint32_t* vertSpv, uint32_t vertSpvSize,
    const uint32_t* fragSpv, uint32_t fragSpvSize,
    bool depthTest, bool blend, bool depthWrite, bool waterPass
) {
    Str3D_Pipeline* pl = (Str3D_Pipeline*)calloc(1, sizeof(Str3D_Pipeline));
    if (!pl) return NULL;
    
    PFN_vkGetInstanceProcAddr gpAddr = ctx->getInstanceProcAddr;
    
    /* Create shader modules */
    PFN_vkCreateShaderModule createShaderModule = 
        (PFN_vkCreateShaderModule)gpAddr(ctx->instance, "vkCreateShaderModule");
    
    VkShaderModuleCreateInfo vertCI = {
        .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
        .codeSize = vertSpvSize,
        .pCode = vertSpv,
    };
    createShaderModule(ctx->device, &vertCI, NULL, &pl->vertModule);
    
    VkShaderModuleCreateInfo fragCI = {
        .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
        .codeSize = fragSpvSize,
        .pCode = fragSpv,
    };
    createShaderModule(ctx->device, &fragCI, NULL, &pl->fragModule);
    
    /* Shader stages */
    VkPipelineShaderStageCreateInfo stages[2] = {
        {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
            .stage = VK_SHADER_STAGE_VERTEX_BIT,
            .module = pl->vertModule,
            .pName = "main",
        },
        {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
            .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
            .module = pl->fragModule,
            .pName = "main",
        },
    };
    
    /* Vertex input — 28-byte interleaved format */
    VkVertexInputBindingDescription bindingDesc = {
        .binding = 0,
        .stride = 28,  /* 3+3+1 floats = 28 bytes */
        .inputRate = VK_VERTEX_INPUT_RATE_VERTEX,
    };
    
    VkVertexInputAttributeDescription attrDescs[3] = {
        { 0, 0, VK_FORMAT_R32G32B32_SFLOAT,  0 },  /* position */
        { 1, 0, VK_FORMAT_R32G32B32_SFLOAT, 12 },  /* color */
        { 2, 0, VK_FORMAT_R32_SFLOAT,        24 },  /* material */
    };
    
    VkPipelineVertexInputStateCreateInfo vertInputCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
        .vertexBindingDescriptionCount = 1,
        .pVertexBindingDescriptions = &bindingDesc,
        .vertexAttributeDescriptionCount = 3,
        .pVertexAttributeDescriptions = attrDescs,
    };
    
    /* Input assembly */
    VkPipelineInputAssemblyStateCreateInfo iaCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
    };
    
    /* Viewport & scissor (dynamic) */
    VkDynamicState dynamicStates[] = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR };
    VkPipelineDynamicStateCreateInfo dynCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
        .dynamicStateCount = 2,
        .pDynamicStates = dynamicStates,
    };
    
    VkPipelineViewportStateCreateInfo vpCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1,
        .scissorCount = 1,
    };
    
    /* Rasterizer */
    VkPipelineRasterizationStateCreateInfo rasterCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .polygonMode = VK_POLYGON_MODE_FILL,
        .cullMode = VK_CULL_MODE_NONE,  /* No backface culling for voxels */
        .lineWidth = 1.0f,
        .depthClampEnable = VK_FALSE,
    };
    
    /* Multisampling */
    VkPipelineMultisampleStateCreateInfo msCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT,
    };
    
    /* Depth stencil */
    VkPipelineDepthStencilStateCreateInfo dsCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO,
        .depthTestEnable = depthTest ? VK_TRUE : VK_FALSE,
        .depthWriteEnable = depthWrite ? VK_TRUE : VK_FALSE,
        .depthCompareOp = VK_COMPARE_OP_LESS,
    };
    
    /* Color blending */
    VkPipelineColorBlendAttachmentState blendAttachment = {
        .colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                          VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT,
        .blendEnable = blend ? VK_TRUE : VK_FALSE,
        .srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA,
        .dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
        .colorBlendOp = VK_BLEND_OP_ADD,
        .srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE,
        .dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO,
        .alphaBlendOp = VK_BLEND_OP_ADD,
    };
    
    VkPipelineColorBlendStateCreateInfo cbCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1,
        .pAttachments = &blendAttachment,
    };
    
    /* Push constant range */
    VkPushConstantRange pushRange = {
        .stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
        .offset = 0,
        .size = STR3D_PUSH_CONST_SIZE,
    };
    
    /* Pipeline layout */
    VkPipelineLayoutCreateInfo layoutCI = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
        .pushConstantRangeCount = 1,
        .pPushConstantRanges = &pushRange,
    };
    
    PFN_vkCreatePipelineLayout createPipelineLayout = 
        (PFN_vkCreatePipelineLayout)gpAddr(ctx->instance, "vkCreatePipelineLayout");
    createPipelineLayout(ctx->device, &layoutCI, NULL, &pl->layout);
    
    /* Create pipeline */
    VkGraphicsPipelineCreateInfo pipelineCI = {
        .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
        .stageCount = 2,
        .pStages = stages,
        .pVertexInputState = &vertInputCI,
        .pInputAssemblyState = &iaCI,
        .pViewportState = &vpCI,
        .pRasterizationState = &rasterCI,
        .pMultisampleState = &msCI,
        .pDepthStencilState = &dsCI,
        .pColorBlendState = &cbCI,
        .pDynamicState = &dynCI,
        .layout = pl->layout,
        .renderPass = sc->renderPass,
        .subpass = 0,
    };
    
    PFN_vkCreateGraphicsPipelines createGraphicsPipelines = 
        (PFN_vkCreateGraphicsPipelines)gpAddr(ctx->instance, "vkCreateGraphicsPipelines");
    if (createGraphicsPipelines(ctx->device, VK_NULL_HANDLE, 1, &pipelineCI, NULL, &pl->pipeline) != VK_SUCCESS) {
        fprintf(stderr, "str3d_pipeline_create: vkCreateGraphicsPipelines failed\n");
    }
    
    pl->vertexStride = 28;
    return pl;
}

/**
 * Convenience: create the world pipeline (depth-tested, opaque).
 */
static inline Str3D_Pipeline* str3d_pipeline_create_world(
    Str3D_Context* ctx, Str3D_Swapchain* sc,
    const uint32_t* vertSpv, uint32_t vertSpvSize,
    const uint32_t* fragSpv, uint32_t fragSpvSize
) {
    return str3d_pipeline_create(ctx, sc, vertSpv, vertSpvSize, fragSpv, fragSpvSize,
                                  true, false, true, false);
}

/**
 * Convenience: create the water pipeline (depth-tested, alpha-blended).
 */
static inline Str3D_Pipeline* str3d_pipeline_create_water(
    Str3D_Context* ctx, Str3D_Swapchain* sc,
    const uint32_t* vertSpv, uint32_t vertSpvSize,
    const uint32_t* fragSpv, uint32_t fragSpvSize
) {
    return str3d_pipeline_create(ctx, sc, vertSpv, vertSpvSize, fragSpv, fragSpvSize,
                                  true, true, true, true);
}

/**
 * Convenience: create the UI pipeline (no depth test, no blending).
 */
static inline Str3D_Pipeline* str3d_pipeline_create_ui(
    Str3D_Context* ctx, Str3D_Swapchain* sc,
    const uint32_t* vertSpv, uint32_t vertSpvSize,
    const uint32_t* fragSpv, uint32_t fragSpvSize
) {
    return str3d_pipeline_create(ctx, sc, vertSpv, vertSpvSize, fragSpv, fragSpvSize,
                                  false, false, true, false);
}

/**
 * Destroys a pipeline and its associated resources.
 */
static inline void str3d_pipeline_destroy(Str3D_Context* ctx, Str3D_Pipeline* pl) {
    if (!ctx || !pl) return;
    
    vkDeviceWaitIdle(ctx->device);
    
    PFN_vkGetInstanceProcAddr gpAddr = ctx->getInstanceProcAddr;
    PFN_vkDestroyPipeline destroyPipeline = (PFN_vkDestroyPipeline)gpAddr(ctx->instance, "vkDestroyPipeline");
    PFN_vkDestroyPipelineLayout destroyPipelineLayout = (PFN_vkDestroyPipelineLayout)gpAddr(ctx->instance, "vkDestroyPipelineLayout");
    PFN_vkDestroyShaderModule destroyShaderModule = (PFN_vkDestroyShaderModule)gpAddr(ctx->instance, "vkDestroyShaderModule");
    
    destroyPipeline(ctx->device, pl->pipeline, NULL);
    destroyPipelineLayout(ctx->device, pl->layout, NULL);
    if (pl->vertModule) destroyShaderModule(ctx->device, pl->vertModule, NULL);
    if (pl->fragModule) destroyShaderModule(ctx->device, pl->fragModule, NULL);
    
    free(pl);
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_PIPELINE_H */
