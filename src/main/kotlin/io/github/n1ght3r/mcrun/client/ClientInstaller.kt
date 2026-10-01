package io.github.n1ght3r.mcrun.client

import com.google.gson.JsonParser
import io.github.n1ght3r.mcrun.download.Checksum
import io.github.n1ght3r.mcrun.download.DownloadException
import io.github.n1ght3r.mcrun.download.Downloader
import org.gradle.api.logging.Logging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * Installs a vanilla client into the shared cache: client jar, libraries, natives and assets.
 * Everything is checksum-verified and reused across projects and versions.
 */
class ClientInstaller(private val downloader: Downloader, private val env: Environment = Environment.current()) {
    private val logger = Logging.getLogger(ClientInstaller::class.java)
    private val cache = downloader.cache

    fun install(versionId: String): LaunchSpec {
        val version = MojangMetadata(downloader).version(versionId)

        val clientJar = cache.clientJar(version.id)
        downloader.download(version.client.url, clientJar, Checksum.sha1(version.client.sha1), version.client.size)

        val libraries = selectLibraries(version.libraries, env)
        val libraryJars = libraries.map { lib ->
            val artifact = lib.artifact!!
            val path = artifact.path ?: throw DownloadException("Library ${lib.name} has no path")
            downloader.download(artifact.url, cache.librariesDir().resolve(path), Checksum.sha1(artifact.sha1), artifact.size)
        }

        val jvmTemplates = version.jvmArguments.filter { RuleEvaluator.allows(it.rules, env) }.flatMap { it.values }
        val gameTemplates = version.gameArguments.filter { RuleEvaluator.allows(it.rules, env) }.flatMap { it.values }

        val nativesDir = cache.nativesDir(version.id)
        val nativeJars = libraries.zip(libraryJars).filter { (lib, _) -> lib.isNative }.map { it.second }
        extractNatives(nativeJars, libraryPathDir(jvmTemplates, nativesDir))

        installAssets(version.assetIndex)

        return LaunchSpec(
            versionId = version.id,
            versionType = version.type,
            mainClass = version.mainClass,
            javaMajor = version.javaMajor,
            // listOf() matters: Path is Iterable<Path>, so `+ clientJar` would append its segments.
            classpath = (libraryJars + listOf(clientJar)).map { it.toAbsolutePath().toString() }.distinct(),
            nativesDirectory = nativesDir.toAbsolutePath().toString(),
            librariesDirectory = cache.librariesDir().toAbsolutePath().toString(),
            assetsRoot = cache.assetsDir().toAbsolutePath().toString(),
            assetIndexName = version.assetIndex.id,
            jvmTemplates = jvmTemplates,
            gameTemplates = gameTemplates,
        )
    }

    /**
     * The directory `-Djava.library.path=` points at. Since 26.1 it is `${natives_directory}/java`
     * rather than `${natives_directory}` itself, so read it from the template.
     */
    private fun libraryPathDir(jvmTemplates: List<String>, nativesDir: Path): Path {
        val template = jvmTemplates.firstOrNull { it.startsWith("-Djava.library.path=") } ?: return nativesDir
        val resolved = template.removePrefix("-Djava.library.path=")
            .replace("\${natives_directory}", nativesDir.toAbsolutePath().toString())
        return if ("\${" in resolved) nativesDir else Path.of(resolved)
    }

    /** Extracts the shared libraries of every native jar, flattened, skipping `META-INF/`. */
    private fun extractNatives(jars: List<Path>, target: Path) {
        Files.createDirectories(target)
        for (jar in jars) {
            ZipFile(jar.toFile()).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue
                    val name = entry.name.substringAfterLast('/')
                    if (NATIVE_SUFFIXES.none { name.endsWith(it) }) continue
                    val out = target.resolve(name)
                    if (Files.isRegularFile(out) && Files.size(out) == entry.size) continue
                    zip.getInputStream(entry).use { input ->
                        val tmp = Files.createTempFile(target, ".$name", ".part")
                        try {
                            Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
                            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING)
                        } catch (e: java.io.IOException) {
                            // A running client of this version holds the file open on Windows.
                            if (!Files.isRegularFile(out)) throw e
                        } finally {
                            Files.deleteIfExists(tmp)
                        }
                    }
                }
            }
        }
    }

    private fun installAssets(ref: AssetIndexRef) {
        val indexFile = cache.assetIndex(ref.id)
        downloader.download(ref.url, indexFile, Checksum.sha1(ref.sha1), ref.size)
        val objects = JsonParser.parseString(Files.readString(indexFile)).asJsonObject.getAsJsonObject("objects")
        val assets = objects.entrySet()
            .map { it.value.asJsonObject }
            .map { it.get("hash").asString to it.get("size").asLong }
            .distinctBy { it.first }
        val missing = assets.filter { (hash, size) ->
            val file = cache.assetObject(hash)
            !Files.isRegularFile(file) || Files.size(file) != size
        }
        if (missing.isEmpty()) return

        logger.lifecycle("mc-run: downloading {} of {} assets for index {}", missing.size, assets.size, ref.id)
        val done = AtomicInteger()
        val pool = Executors.newFixedThreadPool(ASSET_THREADS) { r -> Thread(r, "mc-run-assets").apply { isDaemon = true } }
        try {
            val futures: List<Future<*>> = missing.map { (hash, size) ->
                pool.submit {
                    downloader.downloadUnlocked(
                        "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash",
                        cache.assetObject(hash),
                        Checksum.sha1(hash),
                        size,
                    )
                    val n = done.incrementAndGet()
                    if (n % 500 == 0) logger.lifecycle("mc-run: assets {}/{}", n, missing.size)
                }
            }
            futures.forEach {
                try {
                    it.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    internal companion object {
        private val NATIVE_SUFFIXES = listOf(".dll", ".so", ".dylib", ".jnilib")
        private const val ASSET_THREADS = 16

        /**
         * Rule-allowed libraries with a download. Native variants are narrowed to this machine's
         * arch: the rules only check the OS, so `natives-windows`, `-arm64` and `-x86` would all pass
         * and their extracted files would collide. When a library ships no variant for this arch,
         * the default (x86_64) one is kept.
         */
        fun selectLibraries(libraries: List<Library>, env: Environment): List<Library> {
            val allowed = libraries.filter { it.artifact != null && RuleEvaluator.allows(it.rules, env) }
            val withClassifier = allowed.filter { it.classifier != null }.groupBy { it.baseCoordinates }
            val keep = HashSet<Library>()
            for ((_, variants) in withClassifier) {
                val forArch = variants.filter { RuleEvaluator.classifierArch(it.classifier!!) == env.arch }
                keep += forArch.ifEmpty { variants.filter { RuleEvaluator.classifierArch(it.classifier!!) == "x86_64" } }
            }
            return allowed.filter { it.classifier == null || it in keep }
        }
    }
}
