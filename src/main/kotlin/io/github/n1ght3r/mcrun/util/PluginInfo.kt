package io.github.n1ght3r.mcrun.util

import java.util.Properties

/** Build-time facts about mc-run itself, read from the `mc-run.properties` resource. */
object PluginInfo {
    const val NAME = "mc-run"
    const val REPO_URL = "https://github.com/n1ght3r/mc-run"

    val version: String by lazy {
        val props = Properties()
        PluginInfo::class.java.getResourceAsStream("/mc-run.properties")?.use(props::load)
        props.getProperty("version")?.takeUnless { it.startsWith("\${") } ?: "dev"
    }

    /** Fill and Modrinth ask API clients to identify themselves. */
    val userAgent: String get() = "$NAME/$version (+$REPO_URL)"
}
