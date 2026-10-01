package io.github.n1ght3r.mcrun.server

import io.github.n1ght3r.mcrun.McRunTask
import io.github.n1ght3r.mcrun.ServerType
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault

/** Server inputs shared by `runServer` and `runDev`, wired from `mcRun.server` by the plugin. */
abstract class ServerInputs {
    @get:Input
    abstract val type: Property<ServerType>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val build: Property<Int>

    @get:Input
    abstract val port: Property<Int>

    @get:Input
    abstract val acceptEula: Property<Boolean>

    @get:Internal
    abstract val runDirectory: DirectoryProperty

    /** The plugin under development. Carries the dependency on the task that builds it. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val pluginJars: ConfigurableFileCollection

    @get:Input
    abstract val pluginSources: ListProperty<PluginSource>

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    abstract val args: ListProperty<String>

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    fun toSettings() = ServerSettings(
        type = type.get(),
        version = minecraftVersion.get(),
        build = build.orNull,
        port = port.get(),
        acceptEula = acceptEula.get(),
        runDir = runDirectory.get().asFile,
        pluginJars = pluginJars.files.toList(),
        pluginSources = pluginSources.get(),
        jvmArgs = jvmArgs.get(),
        args = args.get(),
    )
}

/** `runServer`: Paper or Folia in the foreground, console attached. */
@DisableCachingByDefault(because = "Runs a server")
abstract class RunServerTask : McRunTask() {
    @get:Nested
    abstract val server: ServerInputs

    @TaskAction
    fun run() {
        val command = ServerLaunchBuilder.prepare(server.toSettings(), downloader)
        launch(command, server.javaLauncher.get(), "server.args")
    }
}
