import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The plugin itself targets Java 17 so it loads in any Gradle running on 17+.
// The launched server and client pick their own JDK through toolchains.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

gradlePlugin {
    plugins {
        create("mcRun") {
            id = "io.github.n1ght3r.mc-run"
            implementationClass = "io.github.n1ght3r.mcrun.McRunPlugin"
            displayName = "mc-run"
            description = "Launches a Paper/Folia dev server and a vanilla Minecraft client from one build."
        }
    }
}

// Bakes the plugin version into a resource so the User-Agent and ${launcher_version} can read it.
tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("mc-run.properties") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("network")
    }
}

// Tests that hit Mojang, Fill, Modrinth or Hangar. Kept out of the default `test` run.
tasks.register<Test>("networkTest") {
    description = "Runs the network-dependent functional tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("network")
    }
    shouldRunAfter(tasks.test)
}
