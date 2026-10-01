package io.github.n1ght3r.mcrun.dev

import io.github.n1ght3r.mcrun.McRunTask
import io.github.n1ght3r.mcrun.client.ClientInputs
import io.github.n1ght3r.mcrun.server.ServerInputs
import io.github.n1ght3r.mcrun.server.ServerLaunchBuilder
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * `runDev`: starts the server, waits until it is ready, then starts a client that joins it.
 * `JavaExec` blocks, so both processes are managed by [DevSession].
 */
@DisableCachingByDefault(because = "Runs a server and a client")
abstract class RunDevTask : McRunTask() {
    @get:Nested
    abstract val server: ServerInputs

    @get:Nested
    abstract val client: ClientInputs

    @get:Input
    abstract val readyTimeoutSeconds: Property<Int>

    @TaskAction
    fun run() {
        val serverCommand = ServerLaunchBuilder.prepare(server.toSettings(), downloader)
        val clientCommand = client.command()
        DevSession(
            server = DevSession.Launch(
                serverCommand,
                server.javaLauncher.get().executablePath.asFile,
                workDirectory.file("server.args").get().asFile,
            ),
            client = DevSession.Launch(
                clientCommand,
                client.javaLauncher.get().executablePath.asFile,
                workDirectory.file("client.args").get().asFile,
            ),
            readyTimeoutSeconds = readyTimeoutSeconds.get(),
            logger = logger,
        ).run()
    }
}
