package io.github.n1ght3r.mcrun.client

import io.github.n1ght3r.mcrun.McRunTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault

/** Client inputs shared by `runClient` and `runDev`, wired from `mcRun.client` by the plugin. */
abstract class ClientInputs {
    /** Output of `installClient`; carries the task dependency. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val launchSpec: RegularFileProperty

    @get:Input
    abstract val username: Property<String>

    @get:Input
    abstract val autoJoin: Property<Boolean>

    @get:Input
    abstract val serverPort: Property<Int>

    @get:Input
    abstract val writeDefaultOptions: Property<Boolean>

    @get:Internal
    abstract val gameDirectory: DirectoryProperty

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    abstract val args: ListProperty<String>

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    fun toSettings() = ClientSettings(
        username = username.get(),
        autoJoin = autoJoin.get(),
        serverPort = serverPort.get(),
        gameDir = gameDirectory.get().asFile,
        writeDefaultOptions = writeDefaultOptions.get(),
        jvmArgs = jvmArgs.get(),
        args = args.get(),
    )

    fun command() = ClientLaunchBuilder.prepare(LaunchSpec.read(launchSpec.get().asFile), toSettings())
}

/** `runClient`: the vanilla client in the foreground, offline login. */
@DisableCachingByDefault(because = "Runs the game client")
abstract class RunClientTask : McRunTask() {
    @get:Nested
    abstract val client: ClientInputs

    @TaskAction
    fun run() {
        launch(client.command(), client.javaLauncher.get(), "client.args")
    }
}
