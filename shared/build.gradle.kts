import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    // JVM target for development & testing on Windows/Linux/macOS
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    val xcf = XCFramework()

    // Apple targets (compile on macOS only; safe to declare on all platforms)
    listOf(
        iosArm64(),          // iPad physical device
        macosArm64(),        // Apple Silicon Mac
        macosX64()           // Intel Mac
    ).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("com.benasher44:uuid:0.8.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }

        val jvmMain by getting
        val jvmTest by getting

        // Group all Apple targets into a single source set hierarchy
        val appleMain by creating { dependsOn(commonMain) }
        val appleTest by creating { dependsOn(commonTest) }

        val iosArm64Main by getting { dependsOn(appleMain) }
        val iosArm64Test by getting { dependsOn(appleTest) }
        val macosArm64Main by getting { dependsOn(appleMain) }
        val macosArm64Test by getting { dependsOn(appleTest) }
        val macosX64Main by getting { dependsOn(appleMain) }
        val macosX64Test by getting { dependsOn(appleTest) }
    }
}
