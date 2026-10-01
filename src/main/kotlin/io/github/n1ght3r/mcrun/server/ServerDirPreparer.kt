package io.github.n1ght3r.mcrun.server

import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path

/** Prepares the server working directory: `server.properties` and the EULA check. */
object ServerDirPreparer {
    const val EULA_PROPERTY = "-Dcom.mojang.eula.agree=true"

    /**
     * Creates the directory and sets only `online-mode=false` and `server-port=<port>` in
     * `server.properties`. Every other line the developer edited is kept byte for byte.
     */
    fun prepare(runDir: Path, port: Int) {
        Files.createDirectories(runDir)
        val file = runDir.resolve("server.properties")
        val lines = if (Files.isRegularFile(file)) Files.readAllLines(file) else emptyList()
        val patched = patchProperties(lines, linkedMapOf("online-mode" to "false", "server-port" to port.toString()))
        if (patched != lines) Files.write(file, patched)
    }

    /**
     * Fails when the EULA was neither accepted in the DSL nor in `eula.txt`. mc-run never accepts it
     * on the developer's behalf.
     */
    fun checkEula(runDir: Path, acceptEula: Boolean) {
        if (acceptEula) return
        val eula = runDir.resolve("eula.txt")
        val accepted = Files.isRegularFile(eula) && Files.readAllLines(eula).any { it.trim().replace(" ", "") == "eula=true" }
        if (!accepted) {
            throw GradleException(
                "The Minecraft EULA has not been accepted. Read https://aka.ms/MinecraftEULA and, if you agree, " +
                    "set `mcRun { server { acceptEula = true } }` (or write eula=true to $eula).",
            )
        }
    }

    /**
     * Line-based patch of a `.properties` file: replaces the value of each key in [updates] in place,
     * appends keys that are missing, and leaves comments, order and spacing alone.
     */
    fun patchProperties(lines: List<String>, updates: Map<String, String>): List<String> {
        val remaining = LinkedHashMap(updates)
        val result = lines.map { line ->
            val key = keyOf(line) ?: return@map line
            val value = remaining.remove(key) ?: return@map line
            "$key=$value"
        }.toMutableList()
        remaining.forEach { (key, value) -> result += "$key=$value" }
        return result
    }

    private fun keyOf(line: String): String? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return null
        val end = trimmed.indexOfFirst { it == '=' || it == ':' }
        return (if (end < 0) trimmed else trimmed.substring(0, end)).trim()
    }
}
