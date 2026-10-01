package io.github.n1ght3r.mcrun.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MojangManifestTest {
    private val json = """
        {
          "id": "26.2", "type": "release", "mainClass": "net.minecraft.client.main.Main",
          "javaVersion": { "component": "java-runtime-epsilon", "majorVersion": 25 },
          "assetIndex": { "id": "32", "sha1": "aa", "size": 1, "url": "https://example/32.json" },
          "downloads": { "client": { "sha1": "bb", "size": 2, "url": "https://example/client.jar" } },
          "arguments": {
            "game": ["--username", "${'$'}{auth_player_name}",
              { "rules": [{ "action": "allow", "features": { "is_demo_user": true } }], "value": "--demo" }],
            "jvm": [
              { "rules": [{ "action": "allow", "os": { "name": "osx" } }], "value": ["-XstartOnFirstThread"] },
              "-cp", "${'$'}{classpath}"]
          },
          "libraries": [
            { "name": "org.lwjgl:lwjgl:3.4.1", "downloads": { "artifact": { "path": "org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar", "sha1": "c1", "size": 3, "url": "https://example/a.jar" } } },
            { "name": "org.lwjgl:lwjgl:3.4.1:natives-windows", "rules": [{ "action": "allow", "os": { "name": "windows" } }],
              "downloads": { "artifact": { "path": "p1", "sha1": "c2", "size": 3, "url": "https://example/b.jar" } } },
            { "name": "org.lwjgl:lwjgl:3.4.1:natives-windows-arm64", "rules": [{ "action": "allow", "os": { "name": "windows" } }],
              "downloads": { "artifact": { "path": "p2", "sha1": "c3", "size": 3, "url": "https://example/c.jar" } } },
            { "name": "org.lwjgl:lwjgl:3.4.1:natives-macos-arm64", "rules": [{ "action": "allow", "os": { "name": "osx" } }],
              "downloads": { "artifact": { "path": "p3", "sha1": "c4", "size": 3, "url": "https://example/d.jar" } } }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses the fields mc-run needs`() {
        val v = MojangManifest.parseVersion(json)
        assertEquals("26.2", v.id)
        assertEquals(25, v.javaMajor)
        assertEquals("32", v.assetIndex.id)
        assertEquals(4, v.libraries.size)
        assertEquals(listOf("-XstartOnFirstThread"), v.jvmArguments[0].values)
        assertEquals("osx", v.jvmArguments[0].rules!!.single().os!!.name)
        assertTrue(v.libraries[1].isNative)
    }

    @Test
    fun `macOS keeps -XstartOnFirstThread through rule evaluation`() {
        val v = MojangManifest.parseVersion(json)
        val mac = Environment("osx", "arm64", "14.5")
        val jvm = v.jvmArguments.filter { RuleEvaluator.allows(it.rules, mac) }.flatMap { it.values }
        assertEquals("-XstartOnFirstThread", jvm.first())
    }

    @Test
    fun `selects one native variant per library for the current arch`() {
        val v = MojangManifest.parseVersion(json)
        val x64 = ClientInstaller.selectLibraries(v.libraries, Environment("windows", "x86_64", "10.0")).map { it.name }
        assertEquals(listOf("org.lwjgl:lwjgl:3.4.1", "org.lwjgl:lwjgl:3.4.1:natives-windows"), x64)

        val arm = ClientInstaller.selectLibraries(v.libraries, Environment("windows", "arm64", "10.0")).map { it.name }
        assertEquals(listOf("org.lwjgl:lwjgl:3.4.1", "org.lwjgl:lwjgl:3.4.1:natives-windows-arm64"), arm)

        val mac = ClientInstaller.selectLibraries(v.libraries, Environment("osx", "arm64", "14.5")).map { it.name }
        assertEquals(listOf("org.lwjgl:lwjgl:3.4.1", "org.lwjgl:lwjgl:3.4.1:natives-macos-arm64"), mac)
    }
}
