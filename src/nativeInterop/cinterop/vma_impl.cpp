/*
 * VMA (Vulkan Memory Allocator) implementation.
 * Compile with: g++ -c -std=c++11 -DVMA_STATIC_VULKAN_FUNCTIONS=0 vma_impl.cpp
 * Then archive: ar rcs libvma.a vma_impl.o
 */
#define VMA_IMPLEMENTATION
#include "vk_mem_alloc.h"
