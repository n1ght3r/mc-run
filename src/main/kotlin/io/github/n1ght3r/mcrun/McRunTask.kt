package io.github.n1ght3r.mcrun

import io.github.n1ght3r.mcrun.download.DownloadService
import io.github.n1ght3r.mcrun.download.Downloader
import io.github.n1ght3r.mcrun.util.JvmCommand
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Internal
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Base of every mc-run task.
 *
 * The run tasks are plain [DefaultTask]s driving [ExecOperations] instead of `JavaExec`: the
 * classpath, main class and arguments are only known after downloading, and a task's input
 * properties must not change once it has started executing.
 */
@DisableCachingByDefault(because = "Downloads into a shared cache or runs a game process")
abstract class McRunTask : DefaultTask() {
    @get:ServiceReference(DownloadService.NAME)
    abstract val downloads: Property<DownloadService>

    /** Per-task scratch directory under `build/mc-run/`, holds the Java argument files. */
    @get:Internal
    abstract val workDirectory: DirectoryProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    init {
        group = "mc-run"
    }

    @get:Internal
    protected val downloader: Downloader get() = downloads.get().downloader

    /** Runs [command] in the foreground, wired to the Gradle console including stdin. */
    protected fun launch(command: JvmCommand, launcher: JavaLauncher, argFileName: String) {
        val argFile = workDirectory.file(argFileName).get().asFile
        val line = command.commandLine(launcher.executablePath.asFile, argFile)
        command.workingDir.mkdirs()
        execOperations.exec {
            commandLine(line)
            workingDir = command.workingDir
            standardInput = System.`in`
        }
    }
}
