package io.github.n1ght3r.mcrun

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class McRunPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private fun writeProject(extra: String = "") {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"sample\"\n")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                java
                id("io.github.n1ght3r.mc-run")
            }

            mcRun {
                minecraftVersion = "1.21.11"
                javaVersion = 25
                server {
                    type = FOLIA
                    acceptEula = true
                    downloadPlugins {
                        modrinth("viaversion", "5.10.0")
                    }
                }
                client {
                    username = "Tester"
                }
            }
            $extra
            """.trimIndent(),
        )
    }

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")
        .forwardOutput()

    @Test
    fun `registers the tasks and works with the configuration cache`() {
        writeProject()
        val first = runner("tasks", "--group", "mc-run", "--configuration-cache").build()
        listOf("installClient", "runServer", "runClient", "runDev").forEach {
            assertTrue(first.output.contains(it), "missing task $it")
        }
        val second = runner("tasks", "--group", "mc-run", "--configuration-cache").build()
        assertTrue(second.output.contains("Reusing configuration cache"))
    }

    @Test
    fun `runServer depends on the plugin jar and the defaults resolve`() {
        writeProject(
            """
            tasks.register("printMcRun") {
                dependsOn("jar")
                val jar = mcRun.server.pluginJar.map { it.asFile.name }
                val dir = mcRun.server.runDirectory.map { it.asFile.relativeTo(projectDir).invariantSeparatorsPath }
                val game = mcRun.client.gameDirectory.map { it.asFile.relativeTo(projectDir).invariantSeparatorsPath }
                doLast { println("jar=" + jar.get() + " dir=" + dir.get() + " game=" + game.get()) }
            }
            """.trimIndent(),
        )
        val result = runner("printMcRun", "runServer", "--dry-run").build()
        assertTrue(result.output.contains(":jar SKIPPED"), result.output)
        assertTrue(result.output.contains(":runServer SKIPPED"), result.output)

        val printed = runner("printMcRun").build()
        assertTrue(printed.output.contains("jar=sample.jar dir=run/1.21.11/server game=run/1.21.11/client"), printed.output)
        assertEquals(TaskOutcome.SUCCESS, printed.task(":printMcRun")?.outcome)
    }

    @Test
    @Tag("network")
    fun `installClient downloads a working client`() {
        writeProject()
        val cache = File(System.getProperty("user.home"), ".gradle/caches/mc-run")
        val result = runner("installClient", "-Pmcrun.cacheDir=${cache.absolutePath}").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":installClient")?.outcome)
        val spec = File(projectDir, "build/mc-run/client/1.21.11/launch.json")
        assertTrue(spec.isFile)
        assertTrue(spec.readText().contains("net.minecraft.client.main.Main"))
    }
}
