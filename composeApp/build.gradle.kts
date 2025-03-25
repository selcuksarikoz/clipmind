import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation("com.github.kwhat:jnativehook:2.2.2")

        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)

            implementation(compose.ui)
            implementation(compose.material3)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

            implementation("org.jetbrains.compose.material:material-icons-core-desktop:1.3.0")
            implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.3.0")

        }
    }
}

dependencies {

}


compose.desktop {
    application {
        mainClass = "org.kozmonot.clipmind.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Pkg)
            packageName = "ClipMind"
            packageVersion = project.property("version").toString()
            description = "Clipboard history manager"
            copyright = "© 2025. All rights reserved."
            vendor = "Selcuk Sarikoz"

            macOS {
                // Ensure bundleID is consistent with your plist
                bundleID = "org.kozmonot.clipmind"

                // Set the application icon and package icon
                iconFile.set(project.file("src/commonMain/composeResources/drawable/icon.icns"))

                // Add JVM arguments for the dock icon and Apple Silicon support
                jvmArgs += listOf(
                    "-Xdock:icon=${project.projectDir}/src/commonMain/composeResources/drawable/icon.icns",
                    "-Dapple.awt.application.appearance=system", // Proper Dark Mode support
                )

                // Specify architecture for universal binary
                packageBuildVersion = "3.1"
                dmgPackageVersion = "3.1"
                packageVersion = "3.1"

                // Important: Enable universal binary support (both x86_64 and ARM64)
                includeAllModules = true

                val APPLE_ID = env.fetch("APPLE_ID", "default_value")
                val KEYCHAIN_PATH = env.fetch("KEYCHAIN_PATH", "default_value")
                val APPLE_ACCOUNT = env.fetch("APPLE_ACCOUNT", "default_value")
                val APP_PASSWORD = env.fetch("APP_PASSWORD", "default_value")
                val TEAM_ID = env.fetch("TEAM_ID", "default_value")
                
                signing {
                    identity = APPLE_ID
                    keychain = KEYCHAIN_PATH
                    sign.set(true)
                }

                notarization {
                    appleID.set(APPLE_ACCOUNT)
                    password.set(APP_PASSWORD)
                    teamID.set(TEAM_ID)
                }

                // macOS specific app permissions
                infoPlist {
                    extraKeysRawXml = """
                <key>NSClipboardUsageDescription</key>
                <string>ClipMind needs access to your clipboard to monitor and save copied items.</string>
                <key>LSUIElement</key>
                <true/>
                <key>LSBackgroundOnly</key>
                <false/>
                <key>NSHighResolutionCapable</key>
                <true/>
                <key>NSSupportsAutomaticGraphicsSwitching</key>
                <true/>
            """
                }

                // Make the app run in background (for System Tray functionality)
                appCategory = "public.app-category.utilities"
                appStore = true

                // Set minimum OS version - consider 10.15+ for better API support
                minimumSystemVersion = "10.15"
            }

            // For Windows
            windows {
                // Windows specific icon
                iconFile.set(project.file("src/jvmMain/composeResources/icon.ico"))

                // Windows installer settings
                menuGroup = "ClipMind"
                upgradeUuid = "19B996F1-6E18-47D6-9C1E-A02269087D78"
            }

            // For Linux
            linux {
                // Linux icon
                iconFile.set(project.file("src/jvmMain/composeResources/icon.png"))
            }
        }
    }
}

