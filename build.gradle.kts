plugins {
    kotlin("multiplatform") version "2.4.0"
    id("com.android.library") version "8.5.2" apply false
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
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            val sdl3 by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/sdl3.def"))
            }
            val cimgui by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/cimgui.def"))
            }
            val webview by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/webview.def"))
            }
        }
        binaries.executable { entryPoint = "strata.main" }
    }

    // Desktop (Windows) - uses local dev paths
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
            getByName("cimgui") {
                compilerOpts(
                    "-IC:/Users/luis/Dev/Strata/cimgui",
                    "-IC:/Users/luis/Dev/Strata/cimgui/imgui",
                    "-IC:/Users/luis/Dev/Strata/src/nativeInterop/cinterop",
                    "-IC:/VulkanSDK/1.4.357.0/Include"
                )
                linkerOpts("-LC:/Users/luis/Dev/Strata/cimgui/build", "-lcimgui")
            }
            getByName("webview") {
                compilerOpts("-IC:/Users/luis/Dev/Strata/src/nativeInterop/cinterop")
                linkerOpts("-LC:/Users/luis/Dev/Strata/src/nativeInterop/cinterop", "-lstrata_webview")
            }
        }
    }

    // Linux x64 - uses system paths (CI builds SDL3 from source)
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
            getByName("cimgui") {
                compilerOpts("-I/usr/include", "-I${project.file("src/nativeInterop/cinterop")}")
                linkerOpts("-L/usr/lib/x86_64-linux-gnu", "-lcimgui")
            }
            getByName("webview") {
                compilerOpts("-I${project.file("src/nativeInterop/cinterop")}")
                linkerOpts("-L/usr/lib/x86_64-linux-gnu", "-lstrata_webview")
            }
        }
    }

    // Linux ARM64 - cross-compile (CI builds SDL3 for aarch64)
    targets.named<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>("linuxArm64") {
        compilations.getByName("main").cinterops {
            getByName("sdl3") {
                val sdlInclude = System.getenv("SDL3_INCLUDE") ?: "/usr/aarch64-linux-gnu/include"
                val sdlLib = System.getenv("SDL3_LIB") ?: "/usr/aarch64-linux-gnu/lib"
                val vkInclude = System.getenv("VULKAN_INCLUDE") ?: "/usr/include"
                val vkLib = System.getenv("VULKAN_LIB") ?: "/usr/aarch64-linux-gnu/lib"
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
            getByName("cimgui") {
                compilerOpts("-I/usr/include", "-I${project.file("src/nativeInterop/cinterop")}")
                linkerOpts("-L/usr/lib/aarch64-linux-gnu", "-lcimgui")
            }
            getByName("webview") {
                compilerOpts("-I${project.file("src/nativeInterop/cinterop")}")
                linkerOpts("-L/usr/lib/aarch64-linux-gnu", "-lstrata_webview")
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
