package io.github.n1ght3r.mcrun.download

import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** An expected digest of a downloaded file. */
data class Checksum(val algorithm: Algorithm, val hex: String) : Serializable {
    enum class Algorithm(val jcaName: String) { SHA1("SHA-1"), SHA256("SHA-256") }

    fun matches(file: Path): Boolean = Files.isRegularFile(file) && digest(file, algorithm).equals(hex, ignoreCase = true)

    override fun toString(): String = "${algorithm.jcaName}:$hex"

    companion object {
        fun sha1(hex: String) = Checksum(Algorithm.SHA1, hex)

        fun sha256(hex: String) = Checksum(Algorithm.SHA256, hex)

        /** SHA-1 of a string, used to derive stable cache keys from URLs. */
        fun digest(text: String): String =
            MessageDigest.getInstance(Algorithm.SHA1.jcaName)
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun digest(file: Path, algorithm: Algorithm): String {
            val md = MessageDigest.getInstance(algorithm.jcaName)
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    md.update(buffer, 0, read)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
