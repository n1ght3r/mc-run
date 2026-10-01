package io.github.n1ght3r.mcrun.download

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build-scoped holder of the shared [Downloader]. Tasks reach it through `@ServiceReference`, so it
 * is configuration-cache safe and one HTTP client serves the whole build.
 */
abstract class DownloadService : BuildService<DownloadService.Params> {
    interface Params : BuildServiceParameters {
        val cacheDir: DirectoryProperty
        val offline: Property<Boolean>
    }

    val downloader: Downloader by lazy {
        Downloader(Cache(parameters.cacheDir.get().asFile.toPath()), parameters.offline.getOrElse(false))
    }

    companion object {
        const val NAME = "mcRunDownloads"
    }
}
