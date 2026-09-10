import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    mingwX64("desktop")
    linuxX64("linuxX64")
    linuxArm64("linuxArm64")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.vulkan.kotlin)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val desktopMain by getting {
            dependsOn(nativeMain)
        }

        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }

        val linuxArm64Main by getting {
            dependsOn(nativeMain)
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            val sdl3 by creating {
                definitionFile.set(project.layout.projectDirectory.file("src/nativeInterop/cinterop/sdl3.def"))
            }
        }
        binaries.executable {
            entryPoint = "strata.main"
        }
    }
}
