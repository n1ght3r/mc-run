package io.github.n1ght3r.mcrun.client

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files

/**
 * What `installClient` hands to `runClient` / `runDev`: an installed version with rules already
 * applied for this machine, but placeholders still unresolved (they depend on the run settings).
 * Stored as JSON in `build/mc-run/client/<version>/launch.json`.
 */
data class LaunchSpec(
    val versionId: String,
    val versionType: String,
    val mainClass: String,
    val javaMajor: Int?,
    /** Libraries first, client jar last, absolute paths. */
    val classpath: List<String>,
    val nativesDirectory: String,
    val librariesDirectory: String,
    val assetsRoot: String,
    val assetIndexName: String,
    val jvmTemplates: List<String>,
    val gameTemplates: List<String>,
) {
    fun write(file: File) {
        Files.createDirectories(file.toPath().parent)
        Files.writeString(file.toPath(), GSON.toJson(this))
    }

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        fun read(file: File): LaunchSpec = GSON.fromJson(Files.readString(file.toPath()), LaunchSpec::class.java)
    }
}
