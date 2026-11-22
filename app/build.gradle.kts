import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

check(!project.hasProperty("haloLegacyBridge")) { "Legacy bridge builds are no longer supported." }

android {
    namespace = "app.orchard.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.halotv.launcher"
        // Android 5.0. Compose 1.10 and later need 6.0, so the Compose BOM below stays on 1.9.
        minSdk = 21
        targetSdk = 34
        versionCode = 278
        versionName = "3.5.2"
        buildConfigField("String", "HALO_CERT", "\"ed28a19770dac5f926e7adf27224f05dd07d1a6dd54af89fc663bbcf102c41dd\"")

        // `./gradlew -Pdiag ...`: a build for one reporter's TV whose logcat shows no app lines;
        // see system/DiagLog.kt.
        val diag = project.hasProperty("diag")
        buildConfigField("boolean", "DIAG", diag.toString())
        if (diag) versionNameSuffix = "-diag"
    }

    // standard: a normal HOME app. guard: no HOME intent filter at all, for China-market firmwares
    // (some TCL / Hisense builds) that refuse to install any package declaring CATEGORY_HOME. The guard
    // build takes over Home through its accessibility service instead.
    flavorDimensions += "home"
    productFlavors {
        create("standard") {
            dimension = "home"
            manifestPlaceholders["haloUpdateVariant"] = "standard"
            buildConfigField("String", "HALO_EDITION", "\"standard\"")
        }
        create("guard") {
            dimension = "home"
            manifestPlaceholders["haloUpdateVariant"] = "guard"
            buildConfigField("String", "HALO_EDITION", "\"guard\"")
        }
    }

    // The TVs only accept updates signed with the key Aerial was first installed with. That key
    // lives outside version control in local/ (see docs/HANDOFF.md); without it the build falls
    // back to this machine's debug key, which installs fresh but cannot update existing copies.
    val signingProps = rootProject.file("local/independent-signing.properties")
    signingConfigs {
        if (signingProps.exists()) {
            val p = Properties().apply { signingProps.inputStream().use { load(it) } }
            create("aerial") {
                enableV1Signing = true
                enableV2Signing = true
                storeFile = p.getProperty("storeFile").let { if (File(it).isAbsolute) File(it) else rootProject.file("local/" + it) }
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    sourceSets.getByName("release").res.srcDir("src/independent/res")
    sourceSets.getByName("debug").res.srcDir("src/independent/res")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("aerial") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // java.time and friends on Android 5.x to 7.x.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Compose 1.9.5, the last release that runs on Android 5.0 (1.10 raised minSdk to 23).
    val composeBom = platform("androidx.compose:compose-bom:2025.11.01")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.core:core-ktx:1.16.0")
    // Baseline Profile install (src/main/baseline-prof.txt) and its compile state in the diagnostics.
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    // Aerial films. 1.8 is the last Media3 that runs on Android 5.0 (1.9 raised minSdk to 23).
    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-datasource:1.8.1")
    implementation("androidx.media3:media3-database:1.8.1")
    // Phone setup page (a tiny LAN web server) and the QR code that opens it.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
}


/** Build API-specific dex metadata with this exact variant identity for update sessions. */
abstract class DexMetadataAssets : DefaultTask() {
    @get:InputDirectory abstract val dmDir: DirectoryProperty
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val versionCode: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = dmDir.get().asFile
        val map = Properties().apply { File(dir, "dex-metadata-map.properties").inputStream().use { load(it) } }
        val out = File(outputDir.get().asFile, "aerial-dm").apply { deleteRecursively(); mkdirs() }
        val manifest = """{"packageName":"${packageName.get()}","versionCode":${versionCode.get()}}"""
        for (api in listOf("28", "31")) {
            val source = File(dir, map.getProperty(api) ?: error("no dex metadata for API $api in $dir"))
            ZipOutputStream(File(out, "$api.dm").outputStream()).use { zip ->
                ZipFile(source).use { from ->
                    for (entry in from.entries()) {
                        if (entry.name == "manifest.json") continue
                        zip.putNextEntry(ZipEntry(entry.name))
                        from.getInputStream(entry).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray())
                zip.closeEntry()
            }
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<DexMetadataAssets>("${variant.name}DexMetadataAssets") {
            dependsOn("compile${name}ArtProfile")
            dmDir.set(layout.buildDirectory.dir("intermediates/dex_metadata_directory/${variant.name}/compile${name}ArtProfile"))
            packageName.set(variant.applicationId)
            versionCode.set(variant.outputs.single().versionCode.map { it ?: 0 })
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, DexMetadataAssets::outputDir)
    }
}
