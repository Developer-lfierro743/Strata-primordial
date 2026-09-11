plugins {
    kotlin("multiplatform") version "2.4.0"
}

kotlin {
    mingwX64("desktop")
    linuxX64("linuxX64")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.technoirlab.vulkan:vulkan-kotlin:1.4.350-1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
                implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            val sdl3 by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/sdl3.def"))
            }
        }
        binaries.executable { entryPoint = "strata.main" }
    }

    targets.named<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>("desktop") {
        compilations.getByName("main").cinterops {
            getByName("sdl3") {
                compilerOpts(
                    "-IC:/Users/luis/Dev/SDL-main/include",
                    "-IC:/Users/luis/Dev/Strata/src/nativeInterop/cinterop",
                    "-IC:/Users/luis/Dev/Strata/src/nativeInterop/cinterop/strata3d",
                    "-IC:/VulkanSDK/1.4.357.0/Include",
                    "-IC:/Users/luis/Dev/VMA-master/include",
                    "-msse4.2"
                )
                linkerOpts(
                    "-LC:/Users/luis/Dev/SDL-main/build-vs/Release", "-lSDL3",
                    "-LC:/VulkanSDK/1.4.357.0/Lib", "-lvulkan-1",
                    "-LC:/Users/luis/Dev/Strata/src/nativeInterop/cinterop", "-l:libvma.a"
                )
            }
        }
    }

    targets.named<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>("linuxX64") {
        compilations.getByName("main").cinterops {
            getByName("sdl3") {
                val sdlInclude = System.getenv("SDL3_INCLUDE") ?: "/usr/local/include"
                val sdlLib = System.getenv("SDL3_LIB") ?: "/usr/local/lib"
                val vkInclude = System.getenv("VULKAN_INCLUDE") ?: "/usr/include"
                val vkLib = System.getenv("VULKAN_LIB") ?: "/usr/lib/x86_64-linux-gnu"
                val vmaInclude = System.getenv("VMA_INCLUDE") ?: "/usr/include"
                val baseInterop = project.file("src/nativeInterop/cinterop")

                compilerOpts(
                    "-I$sdlInclude",
                    "-I$baseInterop",
                    "-I$baseInterop/strata3d",
                    "-I$vkInclude",
                    "-I$vmaInclude"
                )
                linkerOpts(
                    "-L$sdlLib", "-lSDL3",
                    "-L$vkLib", "-lvulkan",
                    "-L$baseInterop", "-l:libvma.a",
                    "-lm", "-lpthread"
                )
            }
        }
    }
}

tasks.register<Copy>("packageDist") {
    group = "distribution"
    description = "Assemble the release executable + runtime DLLs into build/dist/"
    dependsOn("linkReleaseExecutableDesktop")

    val distDir = layout.buildDirectory.dir("dist/strata")
    val exeDir = layout.buildDirectory.dir("bin/desktop/releaseExecutable")
    val sdlDll = file("C:/Users/luis/Dev/SDL-main/build-vs/Release/SDL3.dll")

    into(distDir)
    from(exeDir) { include("strata-prototype.exe") }
    from(sdlDll)

    doLast {
        println("Packaged Strata distributable in ${distDir.get().asFile}")
    }
}
