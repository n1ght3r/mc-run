package io.github.n1ght3r.mcrun.client

import io.github.n1ght3r.mcrun.util.JvmCommand
import io.github.n1ght3r.mcrun.util.OfflineUuid
import io.github.n1ght3r.mcrun.util.PluginInfo
import java.io.File
import java.io.Serializable
import java.nio.file.Files

/** Plain, serializable snapshot of the `client { }` block, captured by the tasks. */
data class ClientSettings(
    val username: String,
    val autoJoin: Boolean,
    val serverPort: Int,
    val gameDir: File,
    val writeDefaultOptions: Boolean,
    val jvmArgs: List<String>,
    val args: List<String>,
) : Serializable

/** Shared by `runClient` and `runDev`: turns an installed [LaunchSpec] into a command. */
object ClientLaunchBuilder {
    /** First-run `options.txt`: skip the accessibility onboarding and the multiplayer warning. */
    private val DEFAULT_OPTIONS = listOf(
        "onboardAccessibility:false",
        "skipMultiplayerWarning:true",
        "pauseOnLostFocus:false",
        "tutorialStep:none",
    )

    fun prepare(spec: LaunchSpec, settings: ClientSettings): JvmCommand {
        val gameDir = settings.gameDir.toPath()
        Files.createDirectories(gameDir)
        val options = gameDir.resolve("options.txt")
        if (settings.writeDefaultOptions && !Files.exists(options)) {
            Files.write(options, DEFAULT_OPTIONS)
        }

        val values = placeholderValues(spec, settings)
        val jvmArgs = LaunchArguments.resolve(LaunchArguments.stripClasspath(spec.jvmTemplates), values) + settings.jvmArgs
        val gameArgs = buildList {
            addAll(LaunchArguments.resolve(spec.gameTemplates, values))
            if (settings.autoJoin) {
                // Quick Play exists since 1.20; added by hand because its feature flags stay off.
                add("--quickPlayMultiplayer")
                add("localhost:${settings.serverPort}")
            }
            addAll(settings.args)
        }
        return JvmCommand(
            classpath = spec.classpath.map(::File),
            mainClass = spec.mainClass,
            jvmArgs = jvmArgs,
            args = gameArgs,
            workingDir = settings.gameDir,
        )
    }

    fun placeholderValues(spec: LaunchSpec, settings: ClientSettings): Map<String, String> = mapOf(
        "auth_player_name" to settings.username,
        "auth_uuid" to OfflineUuid.undashed(settings.username),
        "auth_access_token" to "0",
        "auth_session" to "0",
        "user_type" to "legacy",
        "user_properties" to "{}",
        "clientid" to "",
        "auth_xuid" to "",
        "version_name" to spec.versionId,
        "version_type" to "release",
        "game_directory" to settings.gameDir.absolutePath,
        "assets_root" to spec.assetsRoot,
        "game_assets" to spec.assetsRoot,
        "assets_index_name" to spec.assetIndexName,
        "natives_directory" to spec.nativesDirectory,
        "launcher_name" to PluginInfo.NAME,
        "launcher_version" to PluginInfo.version,
        "classpath" to spec.classpath.joinToString(File.pathSeparator),
        "classpath_separator" to File.pathSeparator,
        "library_directory" to spec.librariesDirectory,
    )
}
