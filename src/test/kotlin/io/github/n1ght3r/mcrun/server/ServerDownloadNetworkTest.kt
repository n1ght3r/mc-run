package io.github.n1ght3r.mcrun.server

import io.github.n1ght3r.mcrun.ServerType
import io.github.n1ght3r.mcrun.download.Cache
import io.github.n1ght3r.mcrun.download.Downloader
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("network")
class ServerDownloadNetworkTest {
    private val downloader = Downloader(Cache(Path.of(System.getProperty("user.home"), ".gradle", "caches", "mc-run")), offline = false)

    @Test
    fun `resolves the newest stable Paper build`() {
        val jar = PaperFillApi(downloader).resolve(ServerType.PAPER, "1.21.11", null)
        assertTrue(Files.size(jar.path) > 1_000_000)
    }

    @Test
    fun `resolves Modrinth and Hangar plugins`() {
        val downloads = PluginDownloads(downloader)
        assertTrue(Files.isRegularFile(downloads.resolve(PluginSource.Modrinth("viaversion", "5.10.0"))))
        assertTrue(Files.isRegularFile(downloads.resolve(PluginSource.Hangar("ViaVersion", "5.10.0"))))
    }
}
