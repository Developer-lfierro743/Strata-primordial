package strata.vulkan

import kotlinx.cinterop.*

/**
 * Vulkan instance info - common data for renderer setup
 * Actual Vulkan setup will happen in the renderer layer
 */
data class VulkanInstance(
    val applicationName: String = "Strata Primordia",
    val engineName: String = "Strata Engine",
    val apiVersion: Int = 0x00010400u.toInt() // Vulkan 1.4
)

struct VkApplicationInfo(
    val sType: Int = 2, // VK_STRUCTURE_TYPE_APPLICATION_INFO
    val pNext: COpaquePointer? = null,
    val pApplicationName: CPointer<ByteVarOf<Byte>>? = null,
    val applicationVersion: UInt = 1u,
    val pEngineName: CPointer<ByteVarOf<Byte>>? = null,
    val engineVersion: UInt = 1u,
    val apiVersion: UInt = 0x00010400u // Vulkan 1.4
)
