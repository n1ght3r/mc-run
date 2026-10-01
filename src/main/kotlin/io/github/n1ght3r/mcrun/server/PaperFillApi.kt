package io.github.n1ght3r.mcrun.server

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import io.github.n1ght3r.mcrun.ServerType
import io.github.n1ght3r.mcrun.download.Checksum
import io.github.n1ght3r.mcrun.download.DownloadException
import io.github.n1ght3r.mcrun.download.Downloader
import org.gradle.api.logging.Logging
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Resolves Paper and Folia server jars through the Fill v3 API.
 *
 * `GET https://fill.papermc.io/v3/projects/{project}/versions/{version}/builds` returns an array of
 * `{ id, channel, downloads: { "server:default": { name, url, checksums: { sha256 } } } }`.
 * Jars land in `servers/{project}/{version}/{build}.jar` with a `.sha256` sidecar so a pinned build
 * never needs the API again.
 */
class PaperFillApi(private val downloader: Downloader) {
    private val logger = Logging.getLogger(PaperFillApi::class.java)

    data class ServerJar(val path: Path, val build: Int)

    internal data class Build(val id: Int, val channel: String, val url: String, val sha256: String)

    fun resolve(type: ServerType, version: String, pinnedBuild: Int?): ServerJar {
        val dir = downloader.cache.serverDir(type.fillProject, version)

        if (pinnedBuild != null) {
            cachedJar(dir, pinnedBuild)?.let { return it }
        }
        if (downloader.offline) {
            val newest = pinnedBuild?.let { cachedJar(dir, it) } ?: newestCachedJar(dir)
            return newest ?: throw DownloadException(
                "mc-run is offline and no ${type.fillProject} $version jar is cached" +
                    (pinnedBuild?.let { " (build $it)" } ?: ""),
            )
        }

        val url = "https://fill.papermc.io/v3/projects/${type.fillProject}/versions/$version/builds"
        // A "latest" lookup is cached for a few hours; a pinned build that is not cached yet needs a
        // fresh list because it may be newer than the cached one.
        val maxAge = if (pinnedBuild == null) LATEST_CACHE else Duration.ZERO
        val builds = parseBuilds(downloader.cachedText(url, dir.resolve("builds.json"), maxAge))
        if (builds.isEmpty()) throw DownloadException("Fill has no builds for ${type.fillProject} $version")

        val build = pick(builds, pinnedBuild, type, version)
        val jar = dir.resolve("${build.id}.jar")
        downloader.download(build.url, jar, Checksum.sha256(build.sha256))
        Files.writeString(sidecar(jar), build.sha256)
        return ServerJar(jar, build.id)
    }

    private fun pick(builds: List<Build>, pinned: Int?, type: ServerType, version: String): Build {
        if (pinned != null) {
            return builds.firstOrNull { it.id == pinned }
                ?: throw DownloadException("${type.fillProject} $version has no build $pinned")
        }
        val sorted = builds.sortedByDescending { it.id }
        sorted.firstOrNull { it.channel == "STABLE" }?.let { return it }
        val newest = sorted.first()
        logger.warn(
            "mc-run: {} {} has no STABLE build, using build {} from channel {}",
            type.fillProject, version, newest.id, newest.channel,
        )
        return newest
    }

    private fun cachedJar(dir: Path, build: Int): ServerJar? {
        val jar = dir.resolve("$build.jar")
        val sidecar = sidecar(jar)
        if (!Files.isRegularFile(jar) || !Files.isRegularFile(sidecar)) return null
        return if (Checksum.sha256(Files.readString(sidecar).trim()).matches(jar)) ServerJar(jar, build) else null
    }

    private fun newestCachedJar(dir: Path): ServerJar? {
        if (!Files.isDirectory(dir)) return null
        return Files.list(dir).use { files ->
            files.map { it.fileName.toString() }
                .filter { it.endsWith(".jar") }
                .map { it.removeSuffix(".jar").toIntOrNull() }
                .toList()
                .filterNotNull()
                .sortedDescending()
                .firstNotNullOfOrNull { cachedJar(dir, it) }
        }
    }

    private fun sidecar(jar: Path): Path = jar.resolveSibling("${jar.fileName}.sha256")

    companion object {
        private val LATEST_CACHE: Duration = Duration.ofHours(3)

        internal fun parseBuilds(json: String): List<Build> {
            val array: JsonArray = JsonParser.parseString(json).asJsonArray
            return array.mapNotNull { element ->
                val obj = element.asJsonObject
                val download = obj.getAsJsonObject("downloads")?.getAsJsonObject("server:default") ?: return@mapNotNull null
                Build(
                    id = obj.get("id").asInt,
                    channel = obj.get("channel")?.asString ?: "UNKNOWN",
                    url = download.get("url").asString,
                    sha256 = download.getAsJsonObject("checksums").get("sha256").asString,
                )
            }
        }
    }
}
