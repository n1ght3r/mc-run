package io.github.n1ght3r.mcrun.client

import io.github.n1ght3r.mcrun.download.Checksum
import io.github.n1ght3r.mcrun.download.DownloadException
import io.github.n1ght3r.mcrun.download.Downloader
import io.github.n1ght3r.mcrun.util.JavaVersions
import org.gradle.api.logging.Logging
import java.nio.file.Files
import java.time.Duration

/** Cached access to the version manifest and per-version JSON. */
class MojangMetadata(private val downloader: Downloader) {
    private val logger = Logging.getLogger(MojangMetadata::class.java)
    private val cache = downloader.cache

    fun version(id: String): VersionInfo {
        val file = cache.versionJson(id)
        // A version JSON never changes once its sha1 is published, so a cached copy needs no lookup.
        if (Files.isRegularFile(file)) {
            runCatching { return MojangManifest.parseVersion(Files.readString(file)) }
        }
        val entry = manifestEntry(id)
        return MojangManifest.parseVersion(downloader.cachedText(entry.url, file, checksum = Checksum.sha1(entry.sha1)))
    }

    /**
     * Java major for [id]: `javaVersion.majorVersion` from the version JSON, or the
     * [JavaVersions] table when the JSON cannot be read.
     */
    fun javaMajorFor(id: String): Int =
        try {
            version(id).javaMajor ?: JavaVersions.fallbackFor(id)
        } catch (e: Exception) {
            val fallback = JavaVersions.fallbackFor(id)
            logger.warn("mc-run: could not read Mojang metadata for {} ({}), assuming Java {}", id, e.message, fallback)
            fallback
        }

    private fun manifestEntry(id: String): ManifestEntry {
        val file = cache.versionManifest()
        fun lookup(maxAge: Duration) =
            MojangManifest.parseManifest(downloader.cachedText(MojangManifest.URL, file, maxAge)).firstOrNull { it.id == id }
        // Refresh the manifest only when the requested version is missing from the cached one.
        return lookup(Duration.ofDays(36500))
            ?: (if (downloader.offline) null else lookup(Duration.ZERO))
            ?: throw DownloadException("Minecraft version $id is not in Mojang's version manifest")
    }
}
