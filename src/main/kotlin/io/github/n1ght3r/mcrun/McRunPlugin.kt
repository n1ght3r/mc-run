package io.github.n1ght3r.mcrun

import io.github.n1ght3r.mcrun.client.ClientInputs
import io.github.n1ght3r.mcrun.client.InstallClientTask
import io.github.n1ght3r.mcrun.client.MojangMetadata
import io.github.n1ght3r.mcrun.client.RunClientTask
import io.github.n1ght3r.mcrun.dev.RunDevTask
import io.github.n1ght3r.mcrun.download.Cache
import io.github.n1ght3r.mcrun.download.DownloadService
import io.github.n1ght3r.mcrun.download.Downloader
import io.github.n1ght3r.mcrun.server.RunServerTask
import io.github.n1ght3r.mcrun.server.ServerInputs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.File

/**
 * Registers the `mcRun { }` extension and the `installClient`, `runServer`, `runClient` and
 * `runDev` tasks.
 */
class McRunPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaBasePlugin::class.java)

        val ext = project.extensions.create("mcRun", McRunExtension::class.java)
        val cacheDir = project.providers.gradleProperty("mcrun.cacheDir").orNull?.let(::File)
            ?: File(project.gradle.gradleUserHomeDir, "caches/mc-run")
        applyConventions(project, ext)

        val downloads = project.gradle.sharedServices.registerIfAbsent(DownloadService.NAME, DownloadService::class.java) {
            parameters.cacheDir.set(cacheDir)
            parameters.offline.set(ext.offline)
        }

        val launcher = javaLauncher(project, ext, cacheDir)
        val buildDir = project.layout.buildDirectory

        val installClient = project.tasks.register("installClient", InstallClientTask::class.java) {
            usesService(downloads)
            minecraftVersion.set(ext.minecraftVersion)
            launchSpec.set(buildDir.file(ext.minecraftVersion.map { "mc-run/client/$it/launch.json" }))
            workDirectory.set(buildDir.dir("mc-run/$name"))
        }

        project.tasks.register("runServer", RunServerTask::class.java) {
            description = "Runs a Paper or Folia dev server with the plugin under development."
            usesService(downloads)
            workDirectory.set(buildDir.dir("mc-run/$name"))
            wireServer(server, ext, launcher)
        }

        project.tasks.register("runClient", RunClientTask::class.java) {
            description = "Runs the vanilla client with an offline account."
            usesService(downloads)
            workDirectory.set(buildDir.dir("mc-run/$name"))
            wireClient(client, ext, launcher, installClient)
        }

        project.tasks.register("runDev", RunDevTask::class.java) {
            description = "Runs the dev server, waits until it is ready, then runs a client that joins it."
            usesService(downloads)
            workDirectory.set(buildDir.dir("mc-run/$name"))
            readyTimeoutSeconds.set(ext.server.readyTimeoutSeconds)
            wireServer(server, ext, launcher)
            wireClient(client, ext, launcher, installClient)
        }
    }

    private fun applyConventions(project: Project, ext: McRunExtension) {
        val providers = project.providers
        ext.offline.convention(
            providers.gradleProperty("mcrun.offline")
                .map { it.isBlank() || it.toBoolean() }
                .orElse(project.gradle.startParameter.isOffline),
        )

        val runRoot = project.layout.projectDirectory.dir("run")
        with(ext.server) {
            type.convention(ServerType.PAPER)
            port.convention(25565)
            acceptEula.convention(false)
            readyTimeoutSeconds.convention(180)
            runDirectory.convention(runRoot.dir(ext.minecraftVersion.map { "$it/server" }))
        }
        with(ext.client) {
            username.convention("Dev")
            autoJoin.convention(true)
            writeDefaultOptions.convention(true)
            gameDirectory.convention(runRoot.dir(ext.minecraftVersion.map { "$it/client" }))
        }

        // pluginJar: the shadowJar output when Shadow is applied, otherwise the jar output.
        project.pluginManager.withPlugin("java") {
            ext.server.pluginJar.convention(
                project.tasks.named("jar", AbstractArchiveTask::class.java).flatMap { it.archiveFile },
            )
        }
        SHADOW_PLUGIN_IDS.forEach { id ->
            project.pluginManager.withPlugin(id) {
                // Shadow registers shadowJar from its own withPlugin("java") hook, which was added
                // before this one, so the task exists by the time this runs.
                project.pluginManager.withPlugin("java") {
                    ext.server.pluginJar.convention(
                        project.tasks.named("shadowJar", AbstractArchiveTask::class.java).flatMap { it.archiveFile },
                    )
                }
            }
        }
    }

    /**
     * The JDK both processes run on. `mcRun.javaVersion` wins; otherwise Mojang's version JSON
     * decides (read from the shared cache, downloaded on first use).
     */
    private fun javaLauncher(project: Project, ext: McRunExtension, cacheDir: File): Provider<JavaLauncher> {
        val major = ext.javaVersion.orElse(
            ext.minecraftVersion.zip(ext.offline) { version, offline ->
                MojangMetadata(Downloader(Cache(cacheDir.toPath()), offline)).javaMajorFor(version)
            },
        )
        val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
        return toolchains.launcherFor { languageVersion.set(major.map(JavaLanguageVersion::of)) }
    }

    private fun wireServer(server: ServerInputs, ext: McRunExtension, launcher: Provider<JavaLauncher>) {
        val spec = ext.server
        server.type.set(spec.type)
        server.minecraftVersion.set(ext.minecraftVersion)
        server.build.set(spec.build)
        server.port.set(spec.port)
        server.acceptEula.set(spec.acceptEula)
        server.runDirectory.set(spec.runDirectory)
        // An unset pluginJar (no java plugin) must mean "no plugin", not a resolution failure.
        server.pluginJars.from(spec.pluginJar.map { listOf(it) }.orElse(emptyList()))
        server.pluginSources.set(spec.downloadPlugins.sources)
        server.jvmArgs.set(spec.jvmArgs)
        server.args.set(spec.args)
        server.javaLauncher.set(launcher)
    }

    private fun wireClient(
        client: ClientInputs,
        ext: McRunExtension,
        launcher: Provider<JavaLauncher>,
        installClient: TaskProvider<InstallClientTask>,
    ) {
        val spec = ext.client
        client.launchSpec.set(installClient.flatMap { it.launchSpec })
        client.username.set(spec.username)
        client.autoJoin.set(spec.autoJoin)
        client.serverPort.set(ext.server.port)
        client.writeDefaultOptions.set(spec.writeDefaultOptions)
        client.gameDirectory.set(spec.gameDirectory)
        client.jvmArgs.set(spec.jvmArgs)
        client.args.set(spec.args)
        client.javaLauncher.set(launcher)
    }

    private companion object {
        val SHADOW_PLUGIN_IDS = listOf("com.gradleup.shadow", "com.github.johnrengelman.shadow", "io.github.goooler.shadow")
    }
}
