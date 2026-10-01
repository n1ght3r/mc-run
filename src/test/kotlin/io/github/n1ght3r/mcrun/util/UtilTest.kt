package io.github.n1ght3r.mcrun.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class UtilTest {
    @Test
    fun `offline uuid matches what an offline-mode server assigns`() {
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", OfflineUuid.of("Notch").toString())
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", OfflineUuid.undashed("Notch"))
    }

    @Test
    fun `java fallback table`() {
        assertEquals(8, JavaVersions.fallbackFor("1.16.5"))
        assertEquals(16, JavaVersions.fallbackFor("1.17.1"))
        assertEquals(17, JavaVersions.fallbackFor("1.20.4"))
        assertEquals(21, JavaVersions.fallbackFor("1.20.5"))
        assertEquals(21, JavaVersions.fallbackFor("1.21.11"))
        assertEquals(25, JavaVersions.fallbackFor("26.1.2"))
        assertEquals(25, JavaVersions.fallbackFor("26.2"))
    }

    @Test
    fun `arg file quoting escapes backslashes and quotes`() {
        assertEquals("\"C:\\\\a b\\\\c\"", JvmCommand.quoteForArgFile("C:\\a b\\c"))
        assertEquals("\"say \\\"hi\\\"\"", JvmCommand.quoteForArgFile("say \"hi\""))
    }

    @Test
    fun `command line puts options in the arg file`(@org.junit.jupiter.api.io.TempDir dir: File) {
        val cmd = JvmCommand(listOf(File(dir, "a.jar")), "Main", listOf("-Xmx1G"), listOf("--nogui"), dir)
        val argFile = File(dir, "x/run.args")
        val line = cmd.commandLine(File("java"), argFile)
        assertEquals(listOf(File("java").absolutePath, "@${argFile.absolutePath}", "Main", "--nogui"), line)
        assertEquals(3, argFile.readLines().size)
    }
}
