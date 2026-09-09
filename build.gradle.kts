plugins {
    kotlin("multiplatform") version "2.4.0"
}

kotlin {
    mingwX64("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation("io.technoirlab.vulkan:vulkan-kotlin:1.4.350-1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            // KMP Crypto libraries for security module
            implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
            implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
            // JSON serialization for config
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
}

/**
 * Assembles a self-contained, distributable folder under build/dist/.
 * Copies the release executable next to its runtime DLL dependencies so
 * the game launches without SDL3.dll living on the system PATH.
 */
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
