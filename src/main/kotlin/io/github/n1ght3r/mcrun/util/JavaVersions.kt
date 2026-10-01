package io.github.n1ght3r.mcrun.util

/**
 * Fallback mapping from a Minecraft version to the Java major it needs.
 *
 * The primary source is `javaVersion.majorVersion` in Mojang's version JSON (see
 * [io.github.n1ght3r.mcrun.client.MojangMetadata.javaMajorFor]). This table is only used when that
 * JSON cannot be read, for example offline on a fresh machine.
 */
object JavaVersions {
    fun fallbackFor(minecraftVersion: String): Int {
        val parts = minecraftVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return when {
            // Year-based versions (26.1 and later) ship on Java 25.
            major >= 26 -> 25
            major != 1 -> 21
            minor > 20 || (minor == 20 && patch >= 5) -> 21
            minor >= 18 -> 17
            minor == 17 -> 16
            else -> 8
        }
    }
}
