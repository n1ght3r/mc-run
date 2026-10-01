package io.github.n1ght3r.mcrun.client

import io.github.n1ght3r.mcrun.McRunTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** `installClient`: downloads the client into the shared cache and writes `launch.json`. */
@DisableCachingByDefault(because = "Writes into the shared download cache")
abstract class InstallClientTask : McRunTask() {
    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:OutputFile
    abstract val launchSpec: RegularFileProperty

    init {
        description = "Downloads the vanilla client, libraries, natives and assets."
        // The real output is the shared cache, which Gradle does not track. Re-verifying it is
        // cheap once everything is downloaded, so always run.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun install() {
        ClientInstaller(downloader).install(minecraftVersion.get()).write(launchSpec.get().asFile)
    }
}
