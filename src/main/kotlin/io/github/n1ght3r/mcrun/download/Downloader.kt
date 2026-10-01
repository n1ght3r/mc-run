package io.github.n1ght3r.mcrun.download

import io.github.n1ght3r.mcrun.util.PluginInfo
import org.gradle.api.logging.Logging
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant

class DownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * HTTP access with retries, checksum verification and cache-aware reads.
 *
 * Plain class so it works both inside the [DownloadService] build service and at configuration
 * time (the Java version lookup needs Mojang metadata before any task runs).
 */
class Downloader(val cache: Cache, val offline: Boolean) {
    private val logger = Logging.getLogger(Downloader::class.java)

    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    /** GETs [url] as text. Never touches the cache. */
    fun fetchText(url: String): String {
        requireOnline(url)
        return withRetries(url) {
            val response = http.send(request(url), HttpResponse.BodyHandlers.ofString())
            checkStatus(url, response.statusCode())
            response.body()
        }
    }

    /**
     * Returns the text at [url], cached in [file].
     *
     * The cached copy is used as is when it matches [checksum] (immutable content) or is younger than
     * [maxAge]. When the network fails or the build is offline, a stale copy is still better than
     * nothing, so it is returned with a warning.
     */
    fun cachedText(url: String, file: Path, maxAge: Duration? = null, checksum: Checksum? = null): String {
        if (Files.isRegularFile(file)) {
            val fresh = when {
                checksum != null -> checksum.matches(file)
                maxAge != null -> Files.getLastModifiedTime(file).toInstant().plus(maxAge).isAfter(Instant.now())
                else -> false
            }
            if (fresh || offline) return Files.readString(file)
        }
        return try {
            FileLocks.withLock(file) {
                val text = fetchText(url)
                writeAtomically(file) { Files.writeString(it, text) }
                if (checksum != null && !checksum.matches(file)) {
                    Files.deleteIfExists(file)
                    throw DownloadException("Checksum mismatch for $url (expected $checksum)")
                }
                text
            }
        } catch (e: IOException) {
            if (Files.isRegularFile(file)) {
                logger.warn("mc-run: could not refresh {} ({}), using the cached copy", url, e.message)
                Files.readString(file)
            } else {
                throw e
            }
        }
    }

    /**
     * Downloads [url] to [target] unless a valid copy is already there.
     *
     * Valid means: matches [checksum] when one is given, otherwise matches [size] when given,
     * otherwise simply exists. Holds a file lock so concurrent builds do not race.
     */
    fun download(url: String, target: Path, checksum: Checksum? = null, size: Long? = null): Path =
        FileLocks.withLock(target) { downloadUnlocked(url, target, checksum, size) }

    /**
     * Same as [download] without the file lock. Used for the thousands of asset objects, where a
     * race is harmless: both writers produce identical, verified content and the move is atomic.
     */
    fun downloadUnlocked(url: String, target: Path, checksum: Checksum? = null, size: Long? = null): Path {
        if (isValid(target, checksum, size)) return target
        requireOnline(url)
        withRetries(url) {
            writeAtomically(target) { tmp ->
                val response = http.send(request(url), HttpResponse.BodyHandlers.ofFile(tmp))
                checkStatus(url, response.statusCode())
                if (size != null && Files.size(tmp) != size) {
                    throw DownloadException("Size mismatch for $url: expected $size, got ${Files.size(tmp)}")
                }
                if (checksum != null && !checksum.matches(tmp)) {
                    throw DownloadException("Checksum mismatch for $url (expected $checksum)")
                }
            }
        }
        return target
    }

    private fun isValid(target: Path, checksum: Checksum?, size: Long?): Boolean {
        if (!Files.isRegularFile(target)) return false
        return when {
            checksum != null -> checksum.matches(target)
            size != null -> Files.size(target) == size
            else -> true
        }
    }

    private fun request(url: String): HttpRequest =
        HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", PluginInfo.userAgent)
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build()

    private fun checkStatus(url: String, status: Int) {
        if (status !in 200..299) throw HttpStatusException(url, status)
    }

    private fun requireOnline(url: String) {
        if (offline) throw DownloadException("mc-run is offline and $url is not cached")
    }

    private fun <T> withRetries(url: String, attempts: Int = 3, action: () -> T): T {
        var last: IOException? = null
        for (attempt in 1..attempts) {
            try {
                return action()
            } catch (e: HttpStatusException) {
                // A 4xx other than 429 will not get better by asking again.
                if (e.status in 400..499 && e.status != 429) throw e
                last = e
            } catch (e: IOException) {
                last = e
            }
            if (attempt < attempts) {
                logger.info("mc-run: retrying {} after: {}", url, last?.message)
                Thread.sleep(1000L * attempt)
            }
        }
        throw DownloadException("Failed to download $url after $attempts attempts", last)
    }

    private fun writeAtomically(target: Path, write: (Path) -> Unit) {
        Files.createDirectories(target.parent)
        val tmp = Files.createTempFile(target.parent, ".${target.fileName}", ".part")
        try {
            write(tmp)
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: IOException) {
                // Windows refuses to replace a file another process holds open. If an identical
                // copy landed there meanwhile, that is fine.
                if (!Files.isRegularFile(target)) throw e
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    class HttpStatusException(val url: String, val status: Int) : IOException("HTTP $status from $url")
}
