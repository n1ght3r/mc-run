package io.github.n1ght3r.mcrun.util

import java.io.File
import java.io.Serializable
import java.nio.file.Files

/**
 * Everything needed to start one JVM. `RunServerTask` / `RunClientTask` run it through
 * `ExecOperations`, `RunDevTask` through `ProcessBuilder`; both use [commandLine].
 */
data class JvmCommand(
    val classpath: List<File>,
    val mainClass: String,
    val jvmArgs: List<String>,
    val args: List<String>,
    val workingDir: File,
) : Serializable {
    /**
     * The full command line for `java`. JVM options and the classpath go into a Java argument file
     * ([argFile]) because the client classpath alone can exceed the Windows command-line limit.
     */
    fun commandLine(javaExecutable: File, argFile: File): List<String> {
        val options = jvmArgs + listOf("-cp", classpath.joinToString(File.pathSeparator) { it.absolutePath })
        Files.createDirectories(argFile.toPath().parent)
        Files.writeString(argFile.toPath(), options.joinToString("\n") { quoteForArgFile(it) })
        return listOf(javaExecutable.absolutePath, "@${argFile.absolutePath}", mainClass) + args
    }

    companion object {
        /** Quotes a token for a `java @argfile`: backslash is an escape character inside quotes. */
        fun quoteForArgFile(token: String): String =
            "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
