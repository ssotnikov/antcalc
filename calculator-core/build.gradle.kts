import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    val privateProfiles = providers.environmentVariable("ANTCALC_DG7YBN_DIR").orNull
        ?.takeIf { providers.gradleProperty("distributionChannel").orNull == "github-direct" }
    sourceSets.named("main") {
        kotlin.srcDir(privateProfiles?.let { directory ->
            file("$directory/kotlin").also { require(it.isDirectory) { "DG7YBN profile sources are missing" } }
        } ?: "src/publicProfiles/kotlin")
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.gson)
}

tasks.test {
    useJUnit()
}
