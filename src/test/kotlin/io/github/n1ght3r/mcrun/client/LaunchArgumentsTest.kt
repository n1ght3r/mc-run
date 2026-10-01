package io.github.n1ght3r.mcrun.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LaunchArgumentsTest {
    @Test
    fun `substitutes known placeholders`() {
        val out = LaunchArguments.resolve(
            listOf("--username", "\${auth_player_name}", "-Djava.library.path=\${natives_directory}/java"),
            mapOf("auth_player_name" to "Dev", "natives_directory" to "/n"),
        )
        assertEquals(listOf("--username", "Dev", "-Djava.library.path=/n/java"), out)
    }

    @Test
    fun `unknown placeholder drops the value and its flag`() {
        val out = LaunchArguments.resolve(
            listOf("--width", "\${resolution_width}", "--version", "\${version_name}"),
            mapOf("version_name" to "1.21.11"),
        )
        assertEquals(listOf("--version", "1.21.11"), out)
    }

    @Test
    fun `unknown placeholder inside a token drops only that token`() {
        val out = LaunchArguments.resolve(
            listOf("--enable-native-access=ALL-UNNAMED", "-Dfoo=\${missing}"),
            emptyMap(),
        )
        assertEquals(listOf("--enable-native-access=ALL-UNNAMED"), out)
    }

    @Test
    fun `empty bare value drops the flag`() {
        val out = LaunchArguments.resolve(
            listOf("--clientId", "\${clientid}", "--accessToken", "\${auth_access_token}"),
            mapOf("clientid" to "", "auth_access_token" to "0"),
        )
        assertEquals(listOf("--accessToken", "0"), out)
    }

    @Test
    fun `dollar signs in values are literal`() {
        val out = LaunchArguments.resolve(listOf("\${game_directory}"), mapOf("game_directory" to "C:\\a\$b"))
        assertEquals(listOf("C:\\a\$b"), out)
    }

    @Test
    fun `strips the classpath pair`() {
        val out = LaunchArguments.stripClasspath(listOf("-Xss1M", "-cp", "\${classpath}", "-Dx=y"))
        assertEquals(listOf("-Xss1M", "-Dx=y"), out)
    }
}
