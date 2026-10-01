package io.github.n1ght3r.mcrun.download

import java.nio.file.Path

/**
 * Layout of the shared download cache, `~/.gradle/caches/mc-run/` by default.
 *
 * Everything here is shared across projects and Minecraft versions. Writers go through
 * [Downloader], which verifies checksums and holds a file lock while writing.
 */
class Cache(val root: Path) {
    fun serverDir(project: String, version: String): Path = root.resolve("servers").resolve(project).resolve(version)

    fun pluginsDir(): Path = root.resolve("plugins")

    fun clientRoot(): Path = root.resolve("client")

    fun versionManifest(): Path = clientRoot().resolve("version_manifest_v2.json")

    fun versionDir(id: String): Path = clientRoot().resolve("versions").resolve(id)

    fun versionJson(id: String): Path = versionDir(id).resolve("$id.json")

    fun clientJar(id: String): Path = versionDir(id).resolve("$id.jar")

    fun librariesDir(): Path = clientRoot().resolve("libraries")

    fun nativesDir(id: String): Path = clientRoot().resolve("natives").resolve(id)

    fun assetsDir(): Path = clientRoot().resolve("assets")

    fun assetIndex(id: String): Path = assetsDir().resolve("indexes").resolve("$id.json")

    fun assetObject(hash: String): Path = assetsDir().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash)
}
