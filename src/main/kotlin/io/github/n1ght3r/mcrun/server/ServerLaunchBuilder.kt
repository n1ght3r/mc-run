package io.github.n1ght3r.mcrun.server

import io.github.n1ght3r.mcrun.ServerType
import io.github.n1ght3r.mcrun.download.Downloader
import io.github.n1ght3r.mcrun.util.JvmCommand
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import java.io.File
import java.io.Serializable
import java.util.jar.JarFile

/** Plain, serializable snapshot of the `server { }` block, captured by the tasks. */
data class ServerSettings(
    val type: ServerType,
    val version: String,
    val build: Int?,
    val port: Int,
    val acceptEula: Boolean,
    val runDir: File,
    val pluginJars: List<File>,
    val pluginSources: List<PluginSource>,
    val jvmArgs: List<String>,
    val args: List<String>,
) : Serializable

/**
 * Shared by `runServer` and `runDev`: resolves the server jar and extra plugins, prepares the run
 * directory and returns the command to start.
 */
object ServerLaunchBuilder {
    private val logger = Logging.getLogger(ServerLaunchBuilder::class.java)

    fun prepare(settings: ServerSettings, downloader: Downloader): JvmCommand {
        val runDir = settings.runDir.toPath()
        ServerDirPreparer.prepare(runDir, settings.port)
        ServerDirPreparer.checkEula(runDir, settings.acceptEula)

        val server = PaperFillApi(downloader).resolve(settings.type, settings.version, settings.build)
        logger.lifecycle("mc-run: {} {} build {}", settings.type.fillProject, settings.version, server.build)

        val downloads = PluginDownloads(downloader)
        val plugins = settings.pluginJars.filter { it.isFile } + settings.pluginSources.map { downloads.resolve(it).toFile() }
        settings.pluginJars.filterNot { it.isFile }.forEach {
            logger.warn("mc-run: plugin jar {} does not exist, skipping it", it)
        }

        val jvmArgs = buildList {
            if (settings.acceptEula) add(ServerDirPreparer.EULA_PROPERTY)
            addAll(settings.jvmArgs)
        }
        val args = buildList {
            add("--nogui")
            plugins.forEach { add("--add-plugin=${it.absolutePath}") }
            addAll(settings.args)
        }
        return JvmCommand(
            classpath = listOf(server.path.toFile()),
            mainClass = mainClassOf(server.path.toFile()),
            jvmArgs = jvmArgs,
            args = args,
            workingDir = settings.runDir,
        )
    }

    /** Read from the manifest rather than hard-coding Paperclip's entry point. */
    private fun mainClassOf(jar: File): String =
        JarFile(jar).use { it.manifest?.mainAttributes?.getValue("Main-Class") }
            ?: throw GradleException("$jar has no Main-Class in its manifest")
}
