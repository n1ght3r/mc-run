package io.github.n1ght3r.mcrun.client

/**
 * Resolves `${placeholder}` templates from `arguments.jvm` / `arguments.game`.
 */
object LaunchArguments {
    private val PLACEHOLDER = Regex("""\$\{([a-zA-Z0-9_]+)}""")

    /**
     * Substitutes [values] into [templates].
     *
     * An argument with an unknown placeholder is dropped rather than passed through raw. When the
     * dropped argument is a bare value (`${x}`), or resolves to an empty bare value, the `--flag`
     * right before it is dropped too, so no flag is left without its value.
     */
    fun resolve(templates: List<String>, values: Map<String, String>): List<String> {
        val out = mutableListOf<String>()
        for (template in templates) {
            val names = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()
            val bareValue = PLACEHOLDER.matchEntire(template) != null
            if (names.any { it !in values }) {
                if (bareValue) dropDanglingFlag(out)
                continue
            }
            val resolved = PLACEHOLDER.replace(template) { values.getValue(it.groupValues[1]) }
            if (bareValue && resolved.isEmpty()) {
                dropDanglingFlag(out)
                continue
            }
            out += resolved
        }
        return out
    }

    /** Removes `-cp ${classpath}` / `-classpath ${classpath}`: mc-run passes the classpath itself. */
    fun stripClasspath(templates: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < templates.size) {
            val token = templates[i]
            if ((token == "-cp" || token == "-classpath") && templates.getOrNull(i + 1) == "\${classpath}") {
                i += 2
                continue
            }
            out += token
            i++
        }
        return out
    }

    private fun dropDanglingFlag(out: MutableList<String>) {
        val last = out.lastOrNull() ?: return
        if (last.startsWith("--") && '=' !in last) out.removeAt(out.size - 1)
    }
}
