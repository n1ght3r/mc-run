package io.github.n1ght3r.mcrun.server

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ServerDirPreparerTest {
    private val updates = linkedMapOf("online-mode" to "false", "server-port" to "25566")

    @Test
    fun `replaces values in place and keeps everything else`() {
        val before = listOf(
            "#Minecraft server properties",
            "motd=My dev server",
            "online-mode=true",
            "view-distance = 6",
            "server-port=25565",
        )
        val after = ServerDirPreparer.patchProperties(before, updates)
        assertEquals(
            listOf(
                "#Minecraft server properties",
                "motd=My dev server",
                "online-mode=false",
                "view-distance = 6",
                "server-port=25566",
            ),
            after,
        )
    }

    @Test
    fun `appends missing keys`() {
        val after = ServerDirPreparer.patchProperties(listOf("motd=x"), updates)
        assertEquals(listOf("motd=x", "online-mode=false", "server-port=25566"), after)
    }

    @Test
    fun `comments that mention a key are not touched`() {
        val after = ServerDirPreparer.patchProperties(listOf("# online-mode=true is the default"), updates)
        assertEquals("# online-mode=true is the default", after.first())
        assertEquals(3, after.size)
    }

    @Test
    fun `prepare creates server properties`(@TempDir dir: Path) {
        ServerDirPreparer.prepare(dir, 25570)
        val lines = Files.readAllLines(dir.resolve("server.properties"))
        assertTrue("server-port=25570" in lines)
        assertTrue("online-mode=false" in lines)
    }

    @Test
    fun `eula must be accepted explicitly`(@TempDir dir: Path) {
        assertThrows<GradleException> { ServerDirPreparer.checkEula(dir, acceptEula = false) }
        assertDoesNotThrow { ServerDirPreparer.checkEula(dir, acceptEula = true) }
        Files.writeString(dir.resolve("eula.txt"), "#By changing the setting below...\neula=true\n")
        assertDoesNotThrow { ServerDirPreparer.checkEula(dir, acceptEula = false) }
    }
}
