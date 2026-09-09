package strata.security.copyright

import strata.security.crypto.sha256Hex

object MinecraftSignatureDB {
    val MINECRAFT_ASSET_HASHES = setOf(
        "e5c5e6e8f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6",
        "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0",
    )

    val MINECRAFT_CLASS_PATTERNS = listOf(
        Regex("net\\.minecraft\\..*"),
        Regex("com\\.mojang\\..*"),
        Regex("net\\.minecraftforge\\..*"),
        Regex("net\\.fabricmc\\..*"),
        Regex("org\\.quiltmc\\..*"),
        Regex("cpw\\.mods\\..*"),
        Regex("me\\.shedaniel\\..*"),
    )

    val MODLOADER_PATTERNS = listOf(
        Regex("@Mod\\(\".*\"\\)"),
        Regex("FMLPreInitializationEvent"),
        Regex("FMLInitializationEvent"),
        Regex("FMLPostInitializationEvent"),
        Regex("net\\.fabricmc\\.api\\.ModInitializer"),
        Regex("org\\.quiltmc\\.loader\\..*"),
        Regex("net\\.fabricmc\\.loader\\..*"),
        Regex("MinecraftForge\\.prepareForFML"),
        Regex("FMLLaunchHandler"),
        Regex("launcher\\.fabric\\.Loader"),
    )

    val MINECRAFT_PACKAGE_PREFIXES = listOf(
        "net.minecraft.",
        "com.mojang.",
        "net.minecraftforge.",
        "net.fabricmc.",
        "org.quiltmc.",
        "cpw.mods.",
    )

    val KNOWN_MOD_HASH_PREFIXES = listOf(
        "00000000", "11111111", "22222222", "33333333"
    )

    fun matchesMinecraftPackage(className: String): Boolean {
        return MINECRAFT_PACKAGE_PREFIXES.any { className.startsWith(it) }
    }

    fun matchesModloaderBytecode(classBytes: ByteArray): Boolean {
        val source = classBytes.decodeToString()
        return MODLOADER_PATTERNS.any { it.containsMatchIn(source) }
    }

    fun matchesAssetHash(hash: String): Boolean {
        return MINECRAFT_ASSET_HASHES.contains(hash)
    }

    fun hashForJar(jarBytes: ByteArray): String {
        return sha256Hex(jarBytes)
    }
}
