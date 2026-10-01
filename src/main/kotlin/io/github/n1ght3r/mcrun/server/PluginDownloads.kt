package io.github.n1ght3r.mcrun.server

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.n1ght3r.mcrun.download.Checksum
import io.github.n1ght3r.mcrun.download.DownloadException
import io.github.n1ght3r.mcrun.download.Downloader
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Path

/**
 * Turns each [PluginSource] into a cached jar under `plugins/`. The jars are handed to the server
 * with `--add-plugin`, so nothing is ever copied into the server's `plugins/` folder.
 */
class PluginDownloads(private val downloader: Downloader) {
    private val root: Path get() = downloader.cache.pluginsDir()

    fun resolve(source: PluginSource): Path = when (source) {
        is PluginSource.Modrinth -> modrinth(source)
        is PluginSource.Hangar -> hangar(source)
        is PluginSource.Url -> url(source)
    }

    private fun modrinth(source: PluginSource.Modrinth): Path {
        val dir = root.resolve("modrinth").resolve(source.slug).resolve(source.version)
        val metadata = dir.resolve("version.json")
        val api = "https://api.modrinth.com/v2/project/${enc(source.slug)}/version/${enc(source.version)}"
        // A published Modrinth version is immutable, so the metadata is cached forever.
        val version = JsonParser.parseString(downloader.cachedText(api, metadata, maxAge = FOREVER)).asJsonObject
        val files = version.getAsJsonArray("files").map(com.google.gson.JsonElement::getAsJsonObject)
        val file = files.firstOrNull { it.get("primary")?.asBoolean == true } ?: files.firstOrNull()
            ?: throw DownloadException("Modrinth ${source.slug} ${source.version} has no files")
        val name = file.get("filename").asString
        val sha1 = file.getAsJsonObject("hashes").get("sha1").asString
        return downloader.download(file.get("url").asString, dir.resolve(name), Checksum.sha1(sha1))
    }

    private fun hangar(source: PluginSource.Hangar): Path {
        val dir = root.resolve("hangar").resolve(source.slug).resolve(source.version)
        val api = "https://hangar.papermc.io/api/v1/projects/${enc(source.slug)}/versions/${enc(source.version)}"
        val version = JsonParser.parseString(downloader.cachedText(api, dir.resolve("version.json"), maxAge = FOREVER)).asJsonObject
        val paper: JsonObject = version.getAsJsonObject("downloads")?.getAsJsonObject("PAPER")
            ?: throw DownloadException("Hangar ${source.slug} ${source.version} has no PAPER download")
        val info = paper.getAsJsonObject("fileInfo")
        val url = paper.get("downloadUrl")?.takeUnless { it.isJsonNull }?.asString
            ?: throw DownloadException(
                "Hangar ${source.slug} ${source.version} is hosted externally; use url(...) instead",
            )
        val name = info?.get("name")?.asString ?: "${source.slug}-${source.version}.jar"
        val checksum = info?.get("sha256Hash")?.asString?.let(Checksum::sha256)
        return downloader.download(url, dir.resolve(name), checksum)
    }

    private fun url(source: PluginSource.Url): Path {
        val key = Checksum.digest(source.url)
        val name = URI.create(source.url).path.substringAfterLast('/').ifBlank { "plugin.jar" }
            .let { if (it.endsWith(".jar")) it else "$it.jar" }
        val target = root.resolve("url").resolve(key.take(16)).resolve(name)
        return downloader.download(source.url, target, source.sha256?.let(Checksum::sha256))
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private companion object {
        val FOREVER: java.time.Duration = java.time.Duration.ofDays(36500)
    }
}
