package io.github.n1ght3r.mcrun

import io.github.n1ght3r.mcrun.server.PluginSource
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

/**
 * The `mcRun { }` block.
 *
 * ```kotlin
 * mcRun {
 *     minecraftVersion = "1.21.11"
 *     server { type = PAPER; acceptEula = true; downloadPlugins { modrinth("viaversion", "5.10.0") } }
 *     client { username = "Dev" }
 * }
 * ```
 */
abstract class McRunExtension {
    /** Minecraft version shared by the server and the client, e.g. `1.21.11` or `26.2`. */
    abstract val minecraftVersion: Property<String>

    /**
     * Java major used to launch both processes. Defaults to `javaVersion.majorVersion` from
     * Mojang's version JSON.
     */
    abstract val javaVersion: Property<Int>

    /** Use only what is already cached. Defaults to `--offline` or `-Pmcrun.offline`. */
    abstract val offline: Property<Boolean>

    @get:Nested
    abstract val server: ServerSpec

    @get:Nested
    abstract val client: ClientSpec

    fun server(action: Action<in ServerSpec>) = action.execute(server)

    fun client(action: Action<in ClientSpec>) = action.execute(client)
}

abstract class ServerSpec {
    abstract val type: Property<ServerType>

    /** Fixed Fill build number. Unset means the newest STABLE build. */
    abstract val build: Property<Int>

    abstract val port: Property<Int>

    /** Explicit opt-in to the Minecraft EULA. Never defaulted to true. */
    abstract val acceptEula: Property<Boolean>

    /** The plugin under development. Defaults to the `shadowJar` output, else the `jar` output. */
    abstract val pluginJar: RegularFileProperty

    /** Server working directory. Defaults to `run/<minecraftVersion>/server`. */
    abstract val runDirectory: DirectoryProperty

    abstract val jvmArgs: ListProperty<String>

    /** Extra program arguments appended after mc-run's own. */
    abstract val args: ListProperty<String>

    /** How long `runDev` waits for the `Done (...)!` line before failing. */
    abstract val readyTimeoutSeconds: Property<Int>

    @get:Nested
    abstract val downloadPlugins: PluginDownloadsSpec

    fun downloadPlugins(action: Action<in PluginDownloadsSpec>) = action.execute(downloadPlugins)

    // Shorthands so build scripts can write `type = PAPER` without importing ServerType.
    val PAPER: ServerType get() = ServerType.PAPER
    val FOLIA: ServerType get() = ServerType.FOLIA
}

abstract class PluginDownloadsSpec {
    abstract val sources: ListProperty<PluginSource>

    fun modrinth(slug: String, version: String) = sources.add(PluginSource.Modrinth(slug, version))

    fun hangar(slug: String, version: String) = sources.add(PluginSource.Hangar(slug, version))

    @JvmOverloads
    fun url(url: String, sha256: String? = null) = sources.add(PluginSource.Url(url, sha256))
}

abstract class ClientSpec {
    abstract val username: Property<String>

    /** Join the dev server on start with `--quickPlayMultiplayer localhost:<port>`. */
    abstract val autoJoin: Property<Boolean>

    /** Game directory. Defaults to `run/<minecraftVersion>/client`. */
    abstract val gameDirectory: DirectoryProperty

    /** Write first-run `options.txt` defaults (skip onboarding, no pause on focus loss). */
    abstract val writeDefaultOptions: Property<Boolean>

    abstract val jvmArgs: ListProperty<String>

    abstract val args: ListProperty<String>
}
