package io.github.n1ght3r.mcrun.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleEvaluatorTest {
    private val windows = Environment("windows", "x86_64", "10.0")
    private val macArm = Environment("osx", "arm64", "14.5")
    private val linux = Environment("linux", "x86_64", "6.8")

    private fun allow(os: OsRule? = null, features: Map<String, Boolean> = emptyMap()) = Rule("allow", os, features)

    private fun disallow(os: OsRule? = null) = Rule("disallow", os, emptyMap())

    private fun os(name: String? = null, arch: String? = null) = OsRule(name, arch, null, null, null)

    @Test
    fun `no rules means allowed`() {
        assertTrue(RuleEvaluator.allows(null, windows))
        assertTrue(RuleEvaluator.allows(emptyList(), windows))
    }

    @Test
    fun `os name rule matches only that os`() {
        val rules = listOf(allow(os("osx")))
        assertTrue(RuleEvaluator.allows(rules, macArm))
        assertFalse(RuleEvaluator.allows(rules, windows))
    }

    @Test
    fun `last matching rule wins`() {
        val rules = listOf(allow(), disallow(os("linux")))
        assertTrue(RuleEvaluator.allows(rules, windows))
        assertFalse(RuleEvaluator.allows(rules, linux))
    }

    @Test
    fun `arch rule`() {
        val rules = listOf(allow(os(arch = "x86")))
        assertFalse(RuleEvaluator.allows(rules, windows))
        assertTrue(RuleEvaluator.allows(rules, windows.copy(arch = "x86")))
    }

    @Test
    fun `features are all off`() {
        val rules = listOf(allow(features = mapOf("is_demo_user" to true)))
        assertFalse(RuleEvaluator.allows(rules, windows))
        assertTrue(RuleEvaluator.allows(listOf(allow(features = mapOf("is_demo_user" to false))), windows))
    }

    @Test
    fun `os version range`() {
        val min = listOf(allow(OsRule("windows", null, null, "10.0.17134", null)))
        assertFalse(RuleEvaluator.allows(min, windows))
        assertTrue(RuleEvaluator.allows(min, windows.copy(osVersion = "10.0.22631")))
    }

    @Test
    fun `os name and arch mapping`() {
        assertEquals("windows", Environment.osName("Windows 11"))
        assertEquals("osx", Environment.osName("Mac OS X"))
        assertEquals("linux", Environment.osName("Linux"))
        assertEquals("arm64", Environment.arch("aarch64"))
        assertEquals("x86_64", Environment.arch("amd64"))
        assertEquals("x86", Environment.arch("x86"))
    }

    @Test
    fun `classifier arch`() {
        assertEquals("x86_64", RuleEvaluator.classifierArch("natives-windows"))
        assertEquals("arm64", RuleEvaluator.classifierArch("natives-windows-arm64"))
        assertEquals("x86", RuleEvaluator.classifierArch("natives-windows-x86"))
        assertEquals("arm64", RuleEvaluator.classifierArch("natives-macos-arm64"))
        assertEquals("arm64", RuleEvaluator.classifierArch("linux-aarch_64"))
        assertEquals("x86_64", RuleEvaluator.classifierArch("linux-x86_64"))
    }
}
