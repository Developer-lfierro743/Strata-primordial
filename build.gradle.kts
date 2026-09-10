plugins {
    kotlin("multiplatform") version "2.4.20"
}

kotlin {
    mingwX64("desktop")
    linuxX64("linuxX64")
    linuxArm64("linuxArm64")

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

        val desktopMain by getting {
            dependsOn(commonMain)
        }
        val linuxX64Main by getting {
            dependsOn(commonMain)
        }
        val linuxArm64Main by getting {
            dependsOn(commonMain)
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            create("sdl3") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/sdl3.def"))
            }
            create("cimgui") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/cimgui.def"))
            }
            create("webview") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/webview.def"))
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
                val sdlInclude = System.getenv("SDL3_INCLUDE") ?: "/usr/include/SDL3"
                val sdlLib = System.getenv("SDL3_LIB") ?: "/usr/lib/x86_64-linux-gnu"
                val vkInclude = System.getenv("VULKAN_INCLUDE") ?: "/usr/include"
                val vkLib = System.getenv("VULKAN_LIB") ?: "/usr/lib/x86_64-linux-gnu"
                val vmaInclude = System.getenv("VMA_INCLUDE") ?: "/usr/include"

                compilerOpts("-I$sdlInclude", "-I${project.file("src/nativeInterop/cinterop")}", "-I$vkInclude", "-I$vmaInclude")
                linkerOpts("-L$sdlLib", "-lSDL3", "-L$vkLib", "-lvulkan", "-lm", "-lpthread")
            }
        }
    }

    targets.named<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>("linuxArm64") {
        compilations.getByName("main").cinterops {
            getByName("sdl3") {
                val sdlInclude = System.getenv("SDL3_INCLUDE") ?: "/usr/include/SDL3"
                val sdlLib = System.getenv("SDL3_LIB") ?: "/usr/lib/aarch64-linux-gnu"
                val vkInclude = System.getenv("VULKAN_INCLUDE") ?: "/usr/include"
                val vkLib = System.getenv("VULKAN_LIB") ?: "/usr/lib/aarch64-linux-gnu"
                val vmaInclude = System.getenv("VMA_INCLUDE") ?: "/usr/include"

                compilerOpts("-I$sdlInclude", "-I${project.file("src/nativeInterop/cinterop")}", "-I$vkInclude", "-I$vmaInclude")
                linkerOpts("-L$sdlLib", "-lSDL3", "-L$vkLib", "-lvulkan", "-lm", "-lpthread")
            }
        }
    }
}

tasks.register<Copy>("packageDist") {
    group = "distribution"
    description = "Assemble the release executable + runtime DLLs into build/dist/"

    onlyIf { System.getProperty("os.name").lowercase().contains("windows") }

    dependsOn("linkReleaseExecutableDesktop")

    val distDir = layout.buildDirectory.dir("dist/strata")
    val exeDir = layout.buildDirectory.dir("bin/desktop/releaseExecutable")
    val sdlDll = file("C:/Users/luis/Dev/SDL-main/build-vs/Release/SDL3.dll")
    val webviewDll = file("C:/Users/luis/Dev/Strata/src/nativeInterop/cinterop/strata_webview.dll")

    into(distDir)
    from(exeDir) { include("strata-prototype.exe") }
    from(sdlDll)
    from(webviewDll) { onlyIf { webviewDll.exists() } }

    doLast {
        if (webviewDll.exists()) {
            println("Packaged Strata distributable with WebView in ${distDir.get().asFile}")
        } else {
            println("Packaged Strata distributable (no WebView DLL found) in ${distDir.get().asFile}")
        }
    }
}