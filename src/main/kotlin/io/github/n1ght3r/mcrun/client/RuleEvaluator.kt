package io.github.n1ght3r.mcrun.client

/** The machine a rule is evaluated against, in Mojang's vocabulary. */
data class Environment(
    /** `windows`, `osx` or `linux`. */
    val osName: String,
    /** `x86_64`, `arm64` or `x86`. */
    val arch: String,
    val osVersion: String,
    /** Feature flags that are on. mc-run turns all of them off. */
    val features: Set<String> = emptySet(),
) {
    companion object {
        fun current(): Environment = Environment(
            osName = osName(System.getProperty("os.name")),
            arch = arch(System.getProperty("os.arch")),
            osVersion = System.getProperty("os.version") ?: "",
        )

        fun osName(raw: String): String {
            val name = raw.lowercase()
            return when {
                name.startsWith("windows") -> "windows"
                name.startsWith("mac") || name.contains("darwin") -> "osx"
                else -> "linux"
            }
        }

        fun arch(raw: String): String = when (raw.lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "x86", "i386", "i486", "i586", "i686" -> "x86"
            else -> "x86_64"
        }
    }
}

/**
 * Evaluates Mojang `rules` arrays: the last matching rule wins, and no rules means allowed.
 */
object RuleEvaluator {
    fun allows(rules: List<Rule>?, env: Environment): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, env)) allowed = rule.action == "allow"
        }
        return allowed
    }

    private fun matches(rule: Rule, env: Environment): Boolean {
        val os = rule.os
        if (os != null) {
            if (os.name != null && os.name != env.osName) return false
            if (os.arch != null && os.arch != env.arch) return false
            if (os.version != null && !Regex(os.version).containsMatchIn(env.osVersion)) return false
            if (os.versionMin != null && compareVersions(env.osVersion, os.versionMin) < 0) return false
            if (os.versionMax != null && compareVersions(env.osVersion, os.versionMax) > 0) return false
        }
        return rule.features.all { (feature, wanted) -> (feature in env.features) == wanted }
    }

    /**
     * Whether a native library variant fits this machine. Variants of one library differ only in the
     * classifier suffix (`natives-windows`, `natives-windows-arm64`, `natives-windows-x86`,
     * `linux-aarch_64`, ...). No suffix means x86_64.
     */
    fun classifierArch(classifier: String): String {
        val c = classifier.lowercase()
        return when {
            c.contains("arm64") || c.contains("aarch_64") || c.contains("aarch64") -> "arm64"
            c.endsWith("x86") || c.endsWith("x86_32") -> "x86"
            else -> "x86_64"
        }
    }

    internal fun compareVersions(a: String, b: String): Int {
        val left = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val cmp = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }
}
