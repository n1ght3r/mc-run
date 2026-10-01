package io.github.n1ght3r.mcrun.server

import java.io.Serializable

/** One extra server plugin declared in `downloadPlugins { }`. Serializable for the configuration cache. */
sealed interface PluginSource : Serializable {
    /** `GET /v2/project/{slug}/version/{version}` on Modrinth; [version] is the version number. */
    data class Modrinth(val slug: String, val version: String) : PluginSource

    /** Hangar project [slug] at [version], PAPER platform (Folia uses the same files). */
    data class Hangar(val slug: String, val version: String) : PluginSource

    /** A plain URL. Verified only when [sha256] is given. */
    data class Url(val url: String, val sha256: String?) : PluginSource
}
